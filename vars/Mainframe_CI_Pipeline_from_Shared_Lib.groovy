#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import java.net.URL
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/

/**
 Determine the ISPW Path Number for use in Total Test
 @param Level - Level Parameter is the Level returned in the ISPW Webhook
*/
def String getPathNum(String level)
{
    return level.charAt(level.length() - 1)
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
        def ISPW_Release        = pipelineParams.ISPW_Release
        def ISPW_Container      = pipelineParams.ISPW_Container
        def ISPW_Container_Type = pipelineParams.ISPW_Container_Type
        def ISPW_Src_Level      = pipelineParams.ISPW_Src_Level
        def ISPW_Owner          = pipelineParams.ISPW_Owner

        def Git_Project         = pipelineParams.Git_Project

        def CES_Token           = pipelineParams.CES_Token
        def HCI_Conn_ID         = pipelineParams.HCI_Conn_ID
        def HCI_Token           = pipelineParams.HCI_Token
        def CC_repository       = pipelineParams.CC_repository

        def Git_URL             = "https://github.com/${Git_Project}"
        def Git_TTT_Repo        = "${ISPW_Stream}_${ISPW_Application}_Unit_Tests.git"

        echo "Parameters passed:"

        echo "ISPW_Stream:      " + pipelineParams.ISPW_Stream
        echo "ISPW_Application: " + pipelineParams.ISPW_Application
        echo "ISPW_Release:     " + pipelineParams.ISPW_Release
        echo "ISPW_Container:   " + pipelineParams.ISPW_Container
        echo "ISPW_Container_Type: " + pipelineParams.ISPW_Container_Type
        echo "ISPW_Src_Level:   " + pipelineParams.ISPW_Src_Level
        echo "ISPW_Owner:       " + pipelineParams.ISPW_Owner
        echo "Git_Project:      " + pipelineParams.Git_Project
        echo "CES_Token:        " + pipelineParams.CES_Token
        echo "HCI_Conn_ID:      " + pipelineParams.HCI_Conn_ID
        echo "HCI_Token:        " + pipelineParams.HCI_Token
        echo "CC_repository:    " + pipelineParams.CC_repository

        // PipelineConfig is a class storing constants independant from user used throuout the pipeline
        PipelineConfig  pConfig     = new PipelineConfig()
        GitHelper       gitHelper   = new GitHelper(steps)
        MailList        mailList    = new MailList()
        IspwHelper      ispeHelper  = new IspwHelper(steps)

        // Store properties values in variables (easier to retrieve during code)
        def Git_Credentials      = pConfig.Git_Credentials
        def Git_Branch           = pConfig.Git_Branch
        def SQ_Scanner_Name      = pConfig.SQ_Scanner_Name
        def SQ_Server_Name       = pConfig.SQ_Server_Name
        def MF_Source            = pConfig.MF_Source
        def XLR_Template         = pConfig.XLR_Template
        def XLR_User             = pConfig.XLR_User
        def TTT_Folder           = pConfig.TTT_Folder
        def ISPW_URL             = pConfig.ISPW_URL
        def ISPW_Runtime         = pConfig.ISPW_Runtime

        def mailRecipient = mailList.getEmail(ISPW_Owner)

        echo "mailRecipient: " + mailRecipient

        // Determine the current ISPW Path and Level that the code Promotion is from
        def PathNum = getPathNum(ISPW_Src_Level)

        // Use the Path Number to determine the right Runner JCL to use (different STEPLIB concatenations)
        def TTT_Jcl = "Runner_PATH" + PathNum + ".jcl"
        // Also set the Level that the code currently resides in
        def ISPW_Target_Level = "QA" + PathNum

        /*************************************************************************************************************/
        // Build a list of Assignments based on a Set
        // Use httpRequest to get all Tasks for the Set
        def response1 = steps.httpRequest(url: "${ISPW_URL}/ispw/${ISPW_Runtime}/sets/${ISPW_Container}/tasks",
            httpMode: 'GET',
            consoleLogResponseBody: false,
            customHeaders: [[maskValue: true, name: 'authorization', value: "${CES_Token_Clear}"]]
        )

        def setTaskIdList          = getSetTaskIdList(response1, ISPW_Target_Level)

        stage("Retrieve Code From ISPW")
        {
            //Retrieve the code from ISPW that has been promoted 
            checkout([$class: 'IspwContainerConfiguration', 
            componentType: '',                          // optional filter for component types in ISPW
            connectionId: "${HCI_Conn_ID}",     
            credentialsId: "${HCI_Token}",      
            containerName: "${ISPW_Container}",   
            containerType: "${ISPW_Container_Type}",    // 0-Assignment 1-Release 2-Set
            ispwDownloadAll: false,                     // false will not download files that exist in the workspace and haven't previous changed
            serverConfig: '',                           // ISPW runtime config.  if blank ISPW will use the default runtime config
            serverLevel: ''])                           // level to download the components from
        }
    }
}