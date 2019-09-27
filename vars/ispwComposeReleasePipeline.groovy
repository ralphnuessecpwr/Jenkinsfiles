#!/usr/bin/env groovy
import groovy.json.JsonSlurper
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/
PipelineConfig  pConfig         // Pipeline configuration parameters
IspwHelper      ispwHelper      // Helper class for interacting with ISPW

String          cesToken                // Clear text token from CES

private initialize(pipelineParams)
{
    // Clean out any previously downloaded source
    dir(".\\") 
    {
        deleteDir()
    }

    /* Read list of mailaddresses from "private" Config File */
    /* The configFileProvider creates a temporary file on disk and returns its path as variable */
    def mailListlines

    configFileProvider(
        [
            configFile(
                fileId: 'MailList', 
                variable: 'mailListFilePath'
            )
        ]
    ) 
    {
        File mailConfigFile = new File(mailListFilePath)

        if(!mailConfigFile.exists())
        {
            error "File - ${mailListFilePath} - not found! \n Aborting Pipeline"
        }

        mailListlines = mailConfigFile.readLines()
    }

    pipelineParams.ISPW_Assignment  = ''
    pipelineParams.ISPW_Set_Id      = ''
    pipelineParams.ISPW_Owner       = pipelineParams.User_Id
    pipelineParams.ISPW_Src_Level   = 'DEV1'

    // Instantiate and initialize Pipeline Configuration settings
    pConfig     = new   PipelineConfig(
                            steps, 
                            workspace,
                            pipelineParams,
                            mailListlines
                        )

    pConfig.initialize()                                            

    // Use Jenkins Credentials Provider plugin to retrieve CES token in clear text from the Jenkins token for the CES token
    // The clear text token is needed for native http REST requests against the ISPW API
    withCredentials(
        [string(credentialsId: "${pConfig.cesTokenId}", variable: 'cesTokenTemp')]
    ) 
    {
        cesToken = cesTokenTemp
    }

    // Instanatiate and initialize the ISPW Helper
    ispwHelper  = new   IspwHelper(
                            steps, 
                            pConfig
                        )

    def mailMessageExtension
}

/* private method to build the report (mail content) at the end of execution */
private buildReport(componentStatusList)
{
    def componentFailMessage        =   "\nThe program FAILED the Quality gate <sonarGate>, and will be regressed." +
                                        "\nTo review results" +
                                        "\n\n- JUnit reports       : ${BUILD_URL}/testReport/" +
                                        "\n\n- SonarQube dashboard : ${pConfig.sqServerUrl}/dashboard?id=<sonarProject>" +
                                        "\n\n"

    def componentPassMessage        =   "\nThe program PASSED the Quality gate <sonarGate> and may remain in QA." +
                                        "\n\nSonarQube results may be reviewed at ${pConfig.sqServerUrl}/dashboard?id=<sonarProject>" +
                                        "\n\n"

    def reportFailMessage           =   "\n\n\nPrograms FAILING Source or Unit Test Quality Gates:"
    def failingComponentsMessage    =   ''

    def reportPassMessage           =   "\n\n\nPrograms PASSING Source or Unit Test Quality Gates:"
    def passingComponentsMessage    =   ''

    def continueMessage             =   ''
    def mailMessageExtension        =   '\n\n\nDETAIL REPORTS for initial verifications'

    componentStatusList.each
    {
        if(it.value.status == 'FAIL')
        {
            failingComponentsMessage = failingComponentsMessage + "\n\nProgram ${it.key}: "

            if(it.value.utStatus == 'UNKNOWN')
            {
                failingComponentsMessage = failingComponentsMessage + "\n\nNo unit tests were found. Only the source scan was taken into consideration."
            }
            else
            {
                failingComponentsMessage = failingComponentsMessage + "\n\nUnit tests were found and executed."
            }

            componentMessage    = componentFailMessage.replace('<sonarGate>', it.value.sonarGate)
            componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

            failingComponentsMessage = failingComponentsMessage + componentMessage
        }
        else
        {
            passingComponentsMessage = passingComponentsMessage + "\n\nProgram ${it.key}: "

            if(it.value.utStatus == 'UNKNOWN')
            {
                passingComponentsMessage = passingComponentsMessage + "\n\nNo unit tests were found. Only the source scan was taken into consideration."
            }
            else
            {
                passingComponentsMessage = passingComponentsMessage + "\n\nUnit tests were found and executed."
            }

            componentMessage    = componentPassMessage.replace('<sonarGate>', it.value.sonarGate)
            componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

            passingComponentsMessage = passingComponentsMessage + componentMessage
        }
    }

    if(failingComponentsMessage == '')
    {
        failingComponentsMessage    = '\nNone.'
        continueMessage             = '\nExecution of functional tests was triggered. The result was: ' + ftJob.getResult() +
            '\n\nA separated message with details was sent. To review detailed results, use' +
            '\n' + ftJob.getAbsoluteUrl()
    }

    if(passingComponentsMessage == '')
    {
        passingComponentsMessage = '\nNone.'
    }

    mailMessageExtension = continueMessage + 
        mailMessageExtension + 
        reportFailMessage + 
        failingComponentsMessage +
        reportPassMessage +
        passingComponentsMessage

    return mailMessageExtension
}

private checkStatus(componentStatusList)
{
    // if a component fails the source scan it should not be considered for unit testing            
    componentStatusList.each
    {
        if (it.value.status == 'FAIL')
        {
            componentList.remove(it)
            pipelinePass = false
        }
    }
}

private createRelease()
{
    ispwOperation connectionId: pConfig.hciConnId, 
        consoleLogResponseBody: true, 
        credentialsId: pConfig.cesTokenId, 
        ispwAction: 'CreateRelease', 
        ispwRequestBody: """stream=${pConfig.ispwStream}
            application=${pConfig.ispwApplication}
            releaseId=${pConfig.ispwRelease}
            description=Default Description"""

    mailMessageExtension = mailMessageExtension + "Created release " + pConfig.ispwRelease + ".\n"
}

private addAssignments()
{
    def assignmentList = ISPW_Assignment_List.split(',').collect{it.trim() as String}

    assignmentList.each
    {
        def currentAssignment   = it

        def response            = httpRequest(
            url:                        "${pConfig.ispwUrl}/ispw/${pConfig.ispwRuntime}/assignments/${it}/tasks",
            consoleLogResponseBody:     true, 
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            
            )

        def taskList            = new JsonSlurper().parseText(response.getContent()).tasks

        def componentList       = []

        taskList.each
        {
            componentList.add(it.moduleName)        
        }

        taskList = null

        componentList.each
        {
            echo "Task " + it
            
            httpRequest(
                httpMode:                   'POST',
                url:                        "${pConfig.ispwUrl}/ispw/${pConfig.ispwRuntime}/assignments/${currentAssignment}/tasks/transfer?mname=${it}",
                consoleLogResponseBody:     true, 
                contentType:                'APPLICATION_JSON', 
                requestBody:                '''{
                                                "runtimeConfiguration": "''' + pConfig.ispwRuntime + '''",
                                                "containerId": "''' + pConfig.ispwRelease + '''",
                                                "containerType": "R"
                                            }''',
                customHeaders:              [[
                                            maskValue:  true, 
                                            name:       'Authorization', 
                                            value:      cesToken
                                            ]]
            )
        }

        mailMessageExtension = mailMessageExtension + "Added all tasks in assignment " + pConfig.ispwAssigment + " to Release " + pConfig.ispwRelease ".\n"
    }
}

private removeAssignments()
{
    def assignmentList = ISPW_Assignment_List.split(',').collect{it.trim() as String}

    assignmentList.each
    {
        def currentAssignment   = it

        def response            = httpRequest(
            url:                        "${pConfig.ispwUrl}/ispw/${pConfig.ispwRuntime}/assignments/${it}/tasks",
            consoleLogResponseBody:     true, 
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            
            )

        def taskList            = new JsonSlurper().parseText(response.getContent()).tasks

        def componentList       = []

        taskList.each
        {
            componentList.add(it.moduleName)        
        }

        taskList = null

        componentList.each
        {
            echo "Task " + it
            
            httpRequest(
                httpMode:                   'POST',
                url:                        "${pConfig.ispwUrl}/ispw/${pConfig.ispwRuntime}/releases/${pConfig.ispwRelease}/tasks/remove?mname=${it}",
                consoleLogResponseBody:     true, 
                contentType:                'APPLICATION_JSON', 
                requestBody:                '''{
                                                "runtimeConfiguration": "''' + pConfig.ispwRuntime + '''"
                                            }''',
                customHeaders:              [[
                                            maskValue:  true, 
                                            name:       'Authorization', 
                                            value:      cesToken
                                            ]]
            )
        }

        mailMessageExtension = mailMessageExtension + "Removed all tasks in assignment " + pConfig.ispwAssigment + " from Release " + pConfig.ispwRelease ".\n"
    }
}

/**
Call method to execute the pipeline from a shared library
@param pipelineParams - Map of paramter/value pairs
*/
def call(Map pipelineParams)
{
    node
    {
        stage("Initialization")
        {
            initialize(pipelineParams) 
            echo "Determined"
            echo "Application   :" + pConfig.ispwApplication
            echo "Release       :" + pConfig.ispwRelease
        }
                
        /* Download all sources that are part of the container  */
        stage("Determine Action")
        {
            switch(pipelineParams.Release_Action) 
            {
                case "create Release":
                    //createRelease()
                    addAssignments()
                break

                case "add Assignments":
                    addAssignments()
                break

                case "remove Assignments":
                    removeAssignments()
                break

                default:
                    echo "Wrong Action Code"
                break
            }
        }

        // Scan sources and fail fast
        stage("Perform Action")
        {
            echo "Perform Action : " + pipelineParams.Release_Action
        }

        stage("Send Notifications")
        {
            echo "Send Mail"
        }
    }
}
