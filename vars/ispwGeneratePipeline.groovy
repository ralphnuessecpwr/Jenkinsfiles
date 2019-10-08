#!/usr/bin/env groovy
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/*
Helper Classes for the Pipeline Script
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

    // Retrieve the list of COBOL components for the set
    // After each Sonar scan the list will be modified to contain only those components that passed the previous quality gate
    componentList       = ispwHelper.getComponents(cesToken, pConfig.ispwSetId, '2')

    // Instantiate the TTT Helper - initialization will happen at a later point
    tttHelper   = new   TttHelper(
                            this,
                            steps,
                            pConfig
                        )

    // Instantiate and initialize the Sonar Helper
    sonarHelper = new SonarHelper(this, steps, pConfig)
    sonarHelper.initialize()

    sourceResidenceLevel = pConfig.ispwSrcLevel
}

/* private method to build the report (mail content) at the end of execution */
private buildReport(componentStatusList)
{
    def failMessage             =   "\nThe program FAILED the Quality gate <sonarGate>. An attempt to promote the component will not be successful." +
                                    "\nTo review results" +
                                    "\n\n- JUnit reports       : ${BUILD_URL}/testReport/" +
                                    "\n\n- SonarQube dashboard : ${pConfig.sqServerUrl}/dashboard?id=<sonarProject>" +
                                    "\n\n"

    def passMessage             =   "\nThe program PASSED the Quality gate <sonarGate> and may be promoted." +
                                    "\n\nSonarQube results may be reviewed at ${pConfig.sqServerUrl}/dashboard?id=<sonarProject>" +
                                    "\n\n"

    def mailMessageExtension = '\nDETAIL REPORTS' + '\n\nUNIT TEST RESULTS\n'

    componentStatusList.each
    {
        def componentMessage

        mailMessageExtension = mailMessageExtension + "\nProgram ${it.key}: "

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
                
                if(it.value.sourceStatus == 'FAIL')
                {
                    componentMessage    = failMessage.replace('<sonarGate>', it.value.sonarGate)
                    componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

                    mailMessageExtension = mailMessageExtension +
                        componentMessage
                }
                else
                {
                    componentMessage    = passMessage.replace('<sonarGate>', it.value.sonarGate)
                    componentMessage    = componentMessage.replace('<sonarProject>', it.value.sonarProject)

                    mailMessageExtension = mailMessageExtension +
                        componentMessage
                }
            break
        }
    }
    return mailMessageExtension
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
            ispwHelper.downloadSourcesForSet(sourceResidenceLevel)
        }
        
        /* Retrieve the Tests from Github that match that ISPWW Stream and Application */
        stage("Execute Unit Tests")
        {            
            def gitUrlFullPath  = "${pConfig.gitUrl}/${pConfig.gitTttUtRepo}"

            def gitPathList     = []

            // Downnload Unit Test project for each component in the set
            componentList.each
            {
                gitPathList.add("${it}_Unit_Tests")
                
            }

            gitHelper.checkoutPath(gitUrlFullPath, pConfig.gitBranch, gitPathList, pConfig.gitCredentials, pConfig.tttFolder)
            
            /* initialize requires the TTT projects to be present in the Jenkins workspace, therefore it can only execute after downloading from GitHub */
            tttHelper.initialize(componentList)  

            /* Clean up Code Coverage results from previous run */
            //Determine better way to clean up
            //tttHelper.cleanUpCodeCoverageResults()

            /* Execute unit tests and retrieve list of programs that had unit tests*/
            listOfExecutedTargets = tttHelper.loopThruScenarios()
         
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
        stage("Check SonarQube Quality Gate") 
        {
            ispwHelper.downloadCopyBooks(workspace)            

            def sonarProjectName = "${pConfig.ispwStream}_${pConfig.ispwApplication}"

            sonarHelper.scan([
                scanType:           'UT', 
                scanProjectName:    sonarProjectName,
                scanBranch:         pConfig.ispwSetId
            ])

            def qgResult = sonarHelper.checkQualityGate()

            echo "Quality Gate Result " + qgResult
        }

        stage("Send Notifications")
        {
            //def mailMessageExtension = buildReport(componentStatusList)
            def mailMessageExtension = "DUMMY TEXT"

            emailext subject:       '$DEFAULT_SUBJECT',
                        body:       '$DEFAULT_CONTENT \n' + mailMessageExtension,
                        replyTo:    '$DEFAULT_REPLYTO',
                        to:         "${pConfig.mailRecipient}"
        }
    }
}
