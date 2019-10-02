#!/usr/bin/env groovy
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/
PipelineConfig  pConfig         // Pipeline configuration parameters
GitHelper       gitHelper       // Helper class for interacting with git and GitHub
IspwHelper      ispwHelper      // Helper class for interacting with ISPW
TttHelper       tttHelper       // Helper class for interacting with Topaz for Total Test
SonarHelper     sonarHelper     // Helper class for interacting with SonarQube

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

private initialize(pipelineParams)
{
    pipelinePass = true

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

    // Instantiate and initialize Pipeline Configuration settings
    pConfig     = new   PipelineConfig(
                            steps, 
                            workspace,
                            pipelineParams,
                            mailListlines
                        )

    pConfig.initialize()                                            

    // Instantiate and initialize Git Helper
    gitHelper   = new   GitHelper(
                            steps
                        )

    // Use Jenkins Credentials Provider plugin to retrieve GitHub userid and password from gitCredentials token before intializing the Git Helper
    withCredentials([usernamePassword(credentialsId: "${pConfig.gitCredentials}", passwordVariable: 'gitPassword', usernameVariable: 'gitUsername')]) 
    {
        gitHelper.initialize(gitPassword, gitUsername, pConfig.ispwOwner, pConfig.mailRecipient)
    }

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

    componentList           = ispwHelper.getComponents(cesToken, pConfig.ispwAssignment, '0')

    // Build list of status for each component
    componentStatusList = [:]

    componentList.each
    {
        ComponentStatus componentStatus = new ComponentStatus()
        
        componentStatusList[it] = componentStatus
    }

    // Instantiate the TTT Helper - initialization will happen at a later point
    tttHelper   = new   TttHelper(
                            this,
                            steps,
                            pConfig
                        )

    // Instantiate and initialize the Sonar Helper
    sonarHelper = new SonarHelper(this, steps, pConfig)
    sonarHelper.initialize()

    sourceResidenceLevel = pConfig.ispwTargetLevel
}

/* private method to build the report (mail content) at the end of execution */
private buildReport(componentStatusList)
{
    def mailMessageExtension    = 'Functional tests were executed for Programs:'

    listOfExecutedTargets.each
    {
        mailMessageExtension = mailMessageExtension + "\n${it}"
    }

    if(currentBuild.currentResult == 'SUCCESS')
    {
        mailMessageExtension = mailMessageExtension + "\n\nAll tests were PASSED, and the tasks in assignment ${pConfig.ispwAssignment} at level ${pConfig.ispwTargetLevel} MAY BE DEPLOYED."
    }
    else
    {
        mailMessageExtension = mailMessageExtension + "\n\nSome tests were FAILED, and the tasks in assignment ${pConfig.ispwAssignment} at level ${pConfig.ispwTargetLevel} WILL BE REGRESSED." +
            "\n\nTest results may be reviewed at ${BUILD_URL}/testReport/"
    }
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
        }
                
        /* Download all sources that are part of the container  */
        stage("Retrieve Mainframe Code")
        {
            ispwHelper.downloadSourcesForAssignment(sourceResidenceLevel)
            ispwHelper.downloadCopyBooks(workspace)                        
        }

        /* Retrieve the Tests from Github that match that ISPWW Stream and Application */
        stage("Execute Functional Tests")
        {            
            def gitUrlFullPath = "${pConfig.gitUrl}/${pConfig.gitTttFtRepo}"
            
            /* Check out unit tests from GitHub */
            gitHelper.checkout(gitUrlFullPath, pConfig.gitFtBranch, pConfig.gitCredentials, pConfig.tttFolder)

            /* initialize requires the TTT projects to be present in the Jenkins workspace, therefore it can only execute after downloading from GitHub */
            listOfExecutedTargets = tttHelper.initialize(componentList)  

            /* Clean up Code Coverage results from previous run */
            //No CodeCoverage yet 
            //tttHelper.cleanUpCodeCoverageResults()

            /* Execute unit tests and retrieve list of programs that had unit tests*/
            //listOfExecutedTargets = tttHelper.executeFunctionalTests()
            tttHelper.executeFunctionalTests()

            //echo "Executed targets " + listOfExecutedTargets.toString()
         
            tttHelper.passFtResultsToJunit()

            /* push results back to GitHub */
            gitHelper.pushResults(pConfig.gitProject, pConfig.gitTttUtRepo, pConfig.tttFolder, pConfig.gitFtBranch, BUILD_NUMBER)
        }

        /* 
        This stage retrieve Code Coverage metrics from Xpediter Code Coverage for the test executed in the Pipeline
        */ 
        /*
        stage("Collect Metrics")
        {
            tttHelper.collectCodeCoverageResults()
        }
        */

        /* 
        This stage pushes the Source Code, Test Metrics and Coverage metrics into SonarQube and then checks the status of the SonarQube Quality Gate.  
        If the SonarQube quality date fails, the Pipeline fails and stops
        */ 
        /*
        stage("Check Functional Test Quality Gate") 
        {
            componentStatusList = sonarHelper.scanUt(componentList, componentStatusList, listOfExecutedTargets)

            checkStatus(componentStatusList)
        }
        */

        stage("Regress if no success")
        {
            if(currentBuild.currentResult != 'SUCCESS')
            {
                ispwHelper.regressAssignment(pConfig.ispwAssignment, cesToken)
            }
        }

        stage("Send Notifications")
        {
            def mailMessageExtension = buildReport(componentStatusList)

            emailext subject:       '$DEFAULT_SUBJECT',
                        body:       '$DEFAULT_CONTENT \n' + mailMessageExtension,
                        replyTo:    '$DEFAULT_REPLYTO',
                        to:         "${pConfig.mailRecipient}"
        }
    }
}