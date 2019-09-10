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

    componentList           = ispwHelper.getComponents(cesToken, pConfig.ispwContainer, pConfig.ispwContainerType)

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
}

/* private method to build the report (mail content) at the end of execution */
private buildReport(componentStatusList)
{
    def failMessage             =   "\nThe program FAILED the Quality gate <sonarGate>. An attempt to promote the component will not be successful." +
                                    "\nTo review results" +
                                    "\n\n- JUnit reports       : ${BUILD_URL}/testReport/" +
                                    "\n\n- SonarQube dashboard : ${pConfig.sqServerUrl}/dashboard?id=<sonarProject>" +
                                    "\n\nThe component will be regressed."
                                    "\n\n"

    def passMessage             =   "\nThe program PASSED the Quality gate <sonarGate> and may be promoted." +
                                    "\n\nSonarQube results may be reviewed at ${pConfig.sqServerUrl}/dashboard?id=<sonarProject>" +
                                    "\n\n"

    def mailMessageExtension = '\nDETAIL REPORTS' +
        "\n\nSOURCE SCANS"

    componentStatusList.each
    {
        def componentMessage

        mailMessageExtension = mailMessageExtension + 
            "\nProgram ${it.key}: "

        if(it.value.sourceStatus == 'FAIL') 
        {
            componentMessage    = failMessage.replace('<sonarGate>', it.value.sonarGate)
            componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

            mailMessageExtension = mailMessageExtension + componentMessage
        }
        else
        {
            componentMessage    = passMessage.replace('<sonarGate>', it.value.sonarGate)
            componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

            mailMessageExtension = mailMessageExtension + componentMessage
        }

        def mailMessageExtension = mailMessageExtension +
            "\n\nSOURCE SCANS"

        switch(it.value.utStatus) 
        {
            case 'FAIL':
                componentMessage    = failMessage.replace('<sonarGate>', it.value.sonarGate)
                componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

                mailMessageExtension = mailMessageExtension +
                    "Unit tests were found and executed." + 
                    componentMessage
            break

            case 'PASS':
                componentMessage    = passMessage.replace('<sonarGate>', it.value.sonarGate)
                componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

                mailMessageExtension = mailMessageExtension +
                    "Unit tests were found and executed." + 
                    componentMessage
            break

            case 'UNKNOWN':
                mailMessageExtension = mailMessageExtension + 
                    "No unit tests were found. Only the source scan was taken into consideration."
            break
        }
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
            ispwHelper.downloadSources(pConfig.ispwTargetLevel)
        }

        // Scan sources and fail fast
        stage("Scan Sources Only")
        {
            ispwHelper.downloadCopyBooks(workspace)            

            componentStatusList = sonarHelper.scanSources(componentList, componentStatusList)

            checkStatus(componentStatusList)
        }

        /* Retrieve the Tests from Github that match that ISPWW Stream and Application */
        stage("Execute Unit Tests")
        {            
            def gitUrlFullPath = "${pConfig.gitUrl}/${pConfig.gitTttUtRepo}"
            
            /* Check out unit tests from GitHub */
            gitHelper.checkout(gitUrlFullPath, pConfig.gitBranch, pConfig.gitCredentials, pConfig.tttFolder)

            /* initialize requires the TTT projects to be present in the Jenkins workspace, therefore it can only execute after downloading from GitHub */
            /* By now componentList only contains those components that have passed the source scan */
            tttHelper.initialize(componentList)  

            /* Clean up Code Coverage results from previous run */
            tttHelper.cleanUpCodeCoverageResults()

            /* Execute unit tests and retrieve list of programs that had unit tests*/
            listOfExecutedTargets = tttHelper.loopThruScenarios()

            echo "Executed targets " + listOfExecutedTargets.toString()
         
            tttHelper.passResultsToJunit()

            /* push results back to GitHub */
            gitHelper.pushResults(pConfig.gitProject, pConfig.gitTttUtRepo, pConfig.tttFolder, pConfig.gitBranch, BUILD_NUMBER)
        }

        /* 
        This stage retrieve Code Coverage metrics from Xpediter Code Coverage for the test executed in the Pipeline
        */ 
        stage("Collect Metrics")
        {
            tttHelper.collectCodeCoverageResults()
        }

        /* 
        This stage pushes the Source Code, Test Metrics and Coverage metrics into SonarQube and then checks the status of the SonarQube Quality Gate.  
        If the SonarQube quality date fails, the Pipeline fails and stops
        */ 
        stage("Check Unit Test Quality Gate") 
        {
            componentStatusList = sonarHelper.scanUt(componentList, componentStatusList, listOfExecutedTargets)

            checkStatus(componentStatusList)

            /*
                else
                {
                    mailMessageExtension = mailMessageExtension +
                        "\nNo Unit Tests were executed for program ${it}, and only the source was validated. \n\n"
                    
                    programStatusList[it] = 'PASSED'
                } 
            */
        }

        stage("React on previous results")
        {
            if(pipelinePass)
            {
                echo "I would run Functional tests now!"
            }
            else
            {
                componentStatusList.each
                {
                    if(it.value.status == 'FAIL')

                    ispwHelper.regressTask(it.key, cesToken)
                }
            }
        }

//        stage("Trigger XL Release")
//        {
//            /* 
//            This stage triggers a XL Release Pipeline that will move code into the high levels in the ISPW Lifecycle  
//            */
//            xlrHelper.triggerRelease()            
//        }
        
        stage("Send Notifications")
        {
            def mailMessageExtension = buildReport(componentStatusList)

            emailext subject:       '$DEFAULT_SUBJECT',
                        body:       '$DEFAULT_CONTENT \n' + mailMessageExtension,
                        replyTo:    '$DEFAULT_REPLYTO',
                        to:         "${pConfig.mailRecipient}"
        }

        if(pipelineFail)
        {
            currentBuild.result = 'FAILURE'
        }
    }
}