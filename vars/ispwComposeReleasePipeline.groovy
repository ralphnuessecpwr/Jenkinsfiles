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
XlrHelper       xlrHelper       // Helper class for interacting with XLRelease

String          cesToken                // Clear text token from CES
String          mailMessageExtension

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

    // Instantiate and initialize the XLR Helper
    xlrHelper   = new XlrHelper(steps, pConfig)

    mailMessageExtension = ''
}

private createRelease()
{
    def response

    try
    {
        response = ispwOperation connectionId: pConfig.hciConnId, 
            consoleLogResponseBody: true, 
            credentialsId: pConfig.cesTokenId, 
            ispwAction: 'CreateRelease', 
            ispwRequestBody: """stream=${pConfig.ispwStream}
                application=${pConfig.ispwApplication}
                releaseId=${pConfig.ispwRelease}
                description=Default Description"""
        
        mailMessageExtension = mailMessageExtension + "Created release " + pConfig.ispwRelease + ".\n"
    }
    catch (IllegalStateException e)
    {
        mailMessageExtension = mailMessageExtension + "Release " + pConfig.ispwRelease + " already existed. Assignments were added.\n"
    }
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

        mailMessageExtension = mailMessageExtension + "Added all tasks in assignment " + currentAssignment + " to Release " + pConfig.ispwRelease + ".\n"
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

        mailMessageExtension = mailMessageExtension + "Removed all tasks in assignment " + currentAssignment + " from Release " + pConfig.ispwRelease + ".\n"
    }
}

private releaseReady()
{
    def releaseReady        = true

    def failAssignmentList  = []

    def response        = httpRequest(
        url:                        "${pConfig.ispwUrl}/ispw/${pConfig.ispwRuntime}/releases/${pConfig.ispwRelease}/tasks",
        consoleLogResponseBody:     true, 
        customHeaders:              [[
                                    maskValue:  true, 
                                    name:       'authorization', 
                                    value:      "${cesToken}"
                                    ]]
        
        )

    def taskList        = new JsonSlurper().parseText(response.getContent()).tasks

    taskList.each
    {
        if(
            it.level = 'DEV1' ||
            it.level = 'DEV2' ||
            it.level = 'DEV3'
        )
        {
            releaseReady     = false

            if(!failAssignmentList.contains(it.container))
            {
                failAssignmentList.add(it.container)
            }
        }
    }

    return releaseReady
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
        stage("Perform Action")
        {
            switch(pipelineParams.Release_Action) 
            {
                case "create Release":
                    createRelease()
                    addAssignments()
                break

                case "add Assignments":
                    addAssignments()
                break

                case "remove Assignments":
                    removeAssignments()
                break

                case "trigger Release":

                    if(releaseReady())
                    {
                        xlrHelper.triggerRelease()

                        mailMessageExtension = mailMessageExtension + "Triggered XL Release for " + pConfig.ispwRelease + ".\n"
                    }
                    else
                    {
                        mailMessageExtension = mailMessageExtension + "Some assignments for Release " + pConfig.ispwRelease + "still contain tasks in development.\n" +
                            "The release cannot be triggered. Either remove those tasks from the assignments or remove the assignments from the release:\n\n"

                        failAssignmentList.each
                        {
                            mailMessageExtension = mailMessageExtension + it + "\n"
                        }
                    }

                break

                default:
                    echo "Wrong Action Code"
                break
            }
        }

        stage("Send Notifications")
        {
            emailext subject:       'Performed Action ' + pipelineParams.Release_Action + ' on Release ' + pConfig.ispwRelease,
            body:       mailMessageExtension,
            replyTo:    '$DEFAULT_REPLYTO',
            to:         "${pConfig.mailRecipient}"
        }
    }
}
