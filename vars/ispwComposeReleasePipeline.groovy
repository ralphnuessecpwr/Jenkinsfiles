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

def             componentList           // List of components in the triggering set
def             componentStatusList     // List/Map of comonents and their corresponding componentStatus
                                        //  each entry will be of the for [componentName:componentStatus]
                                        //  with componentStatus being an instance of ComponentStatus
                                        //  to get to a status value use
                                        //  componentStatusList[componentName].value.<property>
                                        //  with <property> being one of the properties of a ComponentStatus

def             listOfExecutedTargets   // List of program names for which unit tests have been found and executed
String          cesToken                // Clear text token from CES
def             sourceResidenceLevel    // ISPW level at which the sources reside at the moment

def             ftJob                   // Object returned by triggering an external Jenkins job

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
            steps.error "File - ${mailListFilePath} - not found! \n Aborting Pipeline"
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

    echo "Set User Id : " + pConfig.ispwOwner

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
}

private addAssignments()
{
    def assignmentList = ISPW_Assignment_List.split(',').collect{it.trim() as String}

    assignmentList.each
    {
        def currentAssignment = it

        def response        = steps.httpRequest(
            url:                        "${pConfig.ispwUrl}/ispw/${pConfig.ispwRuntime}/assignments/${it}/tasks",
            consoleLogResponseBody:     false, 
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            
            )
        
        def jsonSlurper = new JsonSlurper()
        def resp        = jsonSlurper.parseText(response.getContent())
        
        response    = null
        jsonSlurper = null

        // Compare the taskIds from the set to all tasks in the release 
        // Where they match, determine the assignment and add it to the list of assignments 
        def taskList = resp.tasks

        def fail    = false

        taskList.each
        {
            if(
                it.level == 'DEV1' ||
                it.level == 'DEV2' ||
                it.level == 'DEV3'
            )
            {
                echo "Wrong level"
                fail = true
            }
        }

        if(!fail)
        {
            echo "Will transfer tasks"

            taskList.each
            {
                echo "Task " + it.moduleName

                ispwOperation connectionId: pConfig.hciConnId, 
                    consoleLogResponseBody: true, 
                    credentialsId: pConfig.cesTokenId, 
                    ispwAction: 'TransferTask', 
                    ispwRequestBody: """runtimeConfiguration=${pConfig.ispwRuntime}
                        assignmentId=${currentAssignment}
                        mname=${it.moduleName}
                        containerId=${pConfig.ispwRelease}
                        containerType=R"""
            }
        }

        resp        = null
        taskList    = null
    }
}

private removeAssignments()
{

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