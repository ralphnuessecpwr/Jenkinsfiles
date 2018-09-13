#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
import com.compuware.devops.util.*

/**
 Determine the ISPW Path Number for use in Total Test
 @param Level - Level Parameter is the Level returned in the ISPW Webhook
*/
 def String getPathNum(String Level)
 {
    return Level.charAt(Level.length() - 1)
 }

/**
Call method to execute the pipeline from a shared library
@param pipelineParams - Map of paramter/value pairs
*/
def call(Map pipelineParams)
{
    node
    {

        // Store parameter values in variables (easier to retrieve during code)
        def ISPW_Stream         = pipelineParams.ISPW_Stream
        def ISPW_Application    = pipelineParams.ISPW_Application
        def ISPW_Container      = pipelineParams.ISPW_Container
        def ISPW_Container_Type = pipelineParams.ISPW_Container_Type
        def ISPW_Src_Level      = pipelineParams.ISPW_Src_Level
        def ISPW_Owner          = pipelineParams.ISPW_Owner

        def Git_Project         = pipelineParams.Git_Project
        def Git_Credentials     = pipelineParams.Git_Credentials

        def CES_Token           = pipelineParams.CES_Token
        def HCI_Conn_ID         = pipelineParams.HCI_Conn_ID
        def HCI_Token           = pipelineParams.HCI_Token
        def CC_repository       = pipelineParams.CC_repository

        def Git_URL             = "https://github.com/${Git_Project}"
        def Git_TTT_Repo        = "${ISPW_Stream}_${ISPW_Application}_Unit_Tests.git"

        // PipelineConfig is a class storing constants independant from user used throuout the pipeline
        PipelineConfig  pConfig     = new PipelineConfig()

        // Store properties values in variables (easier to retrieve during code)
        def Git_Branch           = pConfig.Git_Branch
        def SQ_Scanner_Name      = pConfig.SQ_Scanner_Name
        def SQ_Server_Name       = pConfig.SQ_Server_Name
        def MF_Source            = pConfig.MF_Source
        def XLR_Template         = pConfig.XLR_Template
        def XLR_User             = pConfig.XLR_User
        def TTT_Folder           = pConfig.TTT_Folder
        def ISPW_URL             = pConfig.ISPW_URL
        def ISPW_Runtime         = pConfig.ISPW_Runtime

        GitHelper       gitHelper   = new GitHelper(steps)
        MailList        mailList    = new MailList()
        IspwHelper      ispwHelper  = new IspwHelper(steps, ISPW_URL, ISPW_Runtime, ISPW_Container)

        def mailRecipient = mailList.getEmail(ISPW_Owner)

        // Clean out any previously downloaded source
        dir(".\\") 
        {
            deleteDir()
        }

        def ResponseContentSupplier response1
        def ResponseContentSupplier response2

        /*************************************************************************************************************/
        // Build a list of Assignments based on a Set
        // Use httpRequest to get all Tasks for the Set
        withCredentials(
            [string(credentialsId: "${CES_Token}", variable: 'cesToken')]
        ) 
        {
            response1 = steps.httpRequest(
                url:                    "${ISPW_URL}/ispw/${ISPW_Runtime}/sets/${ISPW_Container}/tasks",
                httpMode:               'GET',
                consoleLogResponseBody: false,
                customHeaders:          [[maskValue: true, name: 'authorization', value: "${cesToken}"]]
            )
        }

        // Use method getSetTaskIdList to extract the list of Task IDs from the response of the httpRequest
        def setTaskIdList   = ispwHelper.getSetTaskIdList(response1, ISPW_Src_Level)
        def setTaskList     = ispwHelper.getSetTaskList(response1, ISPW_Target_Level)

        def ISPW_Release    = ispwHelper.getSetRelease(response1)[0].toString()

        // Use httpRequest to get all Assignments for the Release
        // Need to use two separate objects to store the responses for the httpRequests, 
        // otherwise the script will fail with a NotSerializable Exception
        withCredentials(
            [string(credentialsId: "${CES_Token}", variable: 'cesToken')]
        ) 
        {
            response2 = steps.httpRequest(url: "${ISPW_URL}/ispw/${ISPW_Runtime}/releases/${ISPW_Release}/tasks",
                consoleLogResponseBody: false, 
                customHeaders: [[maskValue: true, name: 'authorization', value: "${cesToken}"]]
            )
        }

        // Use method getAssigmentList to get all Assignments from the Release,
        // that belong to Tasks in the Set
        // If the Sonar Quality Gate fails, these Assignments will be regressed
        def assignmentList      = ispwHelper.getAssigmentList(setTaskIdList, response2)
        setTaskList             = ispwHelper.setTaskVersions(setTaskList, response2, ISPW_Target_Level)

        // Build List of Tags to add to Git
        def gitNewBranch        = assignmentList[0].toString()
        def gitTagList          = []

        for(int i = 0; i < setTaskList.size(); i++)
        {

            gitTag = gitNewBranch + '_' + setTaksList[i].programName + '_' + setTaskList[i].baseVersion

            gitTagList.add(gitTag)

        }
        /*************************************************************************************************************/

        stage("Checkout TTT assets from GitHub")
        {
            Git_Full_URL = Git_Project + '/' + Git_TTT_Repo

            //call gitcheckout wrapper function
            gitcheckout(Git_Full_URL, Git_Branch, Git_Credentials, TTT_Folder)
        }

        stage("Create and push new branch")
        {
            dir("${TTT_Folder}")
            {
                
                bat(script: "git config --global user.name ${ISPW_Owner} \r\ngit config --global user.email ${mailRecipient}")

                stdstatus = bat(returnStatus: true, script: "git branch ${gitNewBranch}")

                if(stdstatus > 0)
                {
                    echo "Branch ${gitNewBranch} already exists - nothing to create"

                    emailBody = "Jenkins Job ${JOB_NAME} was executed because you checked out tasks to ISPW assignment ${gitNewBranch}." +
                                "\n\nBranch ${gitNewBranch} already existed. Make sure you fetched the latest changes for this branch from GitHub."

                }
                else
                {
                    echo "Branch ${gitNewBranch} created"

                    stdout = bat(returnStdout: true, script: "git checkout ${gitNewBranch}")
                    echo "Checkout branch " + stdout

                    // Add Tags to Git 
                    for(int i = 0; i < gitTagList.size(); i++)
                    {
                                                
                        stdout = bat(returnStdout: true, script: "git tag --force -a ${gitTagList[i].toString()} -m ${gitTagList[i].toString()}")
                        echo "Create Tag " + stdout

                    }

                    withCredentials(
                        [usernamePassword(credentialsId: "${Git_Credentials}", passwordVariable: 'gitPassword', usernameVariable: 'gitUsername')]
                    ) 
                    {              
                        /*  
                        stdout = bat(returnStdout: true, script: "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${Git_Project}/${Git_TTT_Repo} HEAD:${gitNewBranch} -f --tags")
                        echo "pushed " + stdout
                        */
                    }

                    emailBody = "Jenkins Job ${JOB_NAME} was executed because you checked out tasks to ISPW assignment ${gitNewBranch}." +
                                "\n\nBranch ${gitNewBranch} was created. Fetch this branch from GitHub to start work on your Unit Tests:" + 
                                "\n- Use 'Fetch from Upstream' to fetch the remote branch." +
                                "\n- And 'Checkout' the remote branch 'origin/${gitNewBranch}' as local branch."

                }

                emailext subject:   "Git assignment branch ${gitNewBranch}",
                    body:           emailBody + "\n\n" + '$DEFAULT_CONTENT',
                    replyTo:        '$DEFAULT_REPLYTO',
                    to:             "${mailRecipient}"
            }
        }
    }
}