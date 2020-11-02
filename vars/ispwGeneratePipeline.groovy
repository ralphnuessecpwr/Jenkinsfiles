#!/usr/bin/env groovy
/*
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
*/
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

String          cesToken                // Clear text token from CES
def             sourceResidenceLevel    // ISPW level at which the sources reside at the moment

def getSonarResults(resultsFile){

    def resultsList         = ''
    def resultsFileContent  = readFile(file: sonarResultsFolder + '/' + resultsFile)
    resultsFileContent      = resultsFileContent.substring(resultsFileContent.indexOf('\n') + 1)
    def testExecutions      = new XmlSlurper().parseText(resultsFileContent)

    testExecutions.file.each {

        resultsList = resultsList + it.@path.toString().replace('.result', '.sonar.xml') + ','

    }

    return resultsList
}

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
            def gitUrlFullPath = "${pConfig.gitUrl}/${pConfig.gitTttUtRepo}"
            
            /* Check out unit tests from GitHub */
            gitHelper.checkout(gitUrlFullPath, pConfig.gitBranch, pConfig.gitCredentials, pConfig.tttFolder)

            /* Clean up Code Coverage results from previous run */
            tttHelper.cleanUpCodeCoverageResults()

            totaltest(
                serverUrl:                          pConfig.ispwUrl, 
                credentialsId:                      pConfig.hciTokenId, 
                environmentId:                      '5cee98c2d3142c1f90a4976d',
                localConfig:                        false, 
                folderPath:                         pConfig.tttFolder, 
                recursive:                          true, 
                selectProgramsOption:               true, 
                jsonFile:                           '',
                haltPipelineOnFailure:              false,                 
                stopIfTestFailsOrThresholdReached:  false,
                collectCodeCoverage:                true,
                collectCCRepository:                pConfig.ccRepository,
                collectCCSystem:                    pConfig.ispwApplication,
                collectCCTestID:                    BUILD_NUMBER,
                clearCodeCoverage:                  false,
                //ccThreshold:                        pipelineParms.ccThreshold,     
                logLevel:                           'INFO'
            )
         
            tttHelper.passResultsToJunit()

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

            def scannerHome = tool "scanner"
            def sonarResults    = getSonarResults('generated.cli.UT.suite.sonar.xml')

            withSonarQubeEnv("localhost") {

                bat '"' + scannerHome + '/bin/sonar-scanner"' + 
            //    ' -Dsonar.branch.name=' + executionBranch +
                ' -Dsonar.projectKey=' + pConfig.ispwStream + '_' + pConfig.ispwApplication + 
                ' -Dsonar.projectName=' + pConfig.ispwStream + '_' + pConfig.ispwApplication +
                ' -Dsonar.projectVersion=1.0' +
                " -Dsonar.sources=${pConfig.ispwApplication}\\${pConfig.mfSourceFolder}" +
                " -Dsonar.cobol.copy.directories=${pConfig.ispwApplication}\\${pConfig.mfSourceFolder}" +
                ' -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,result' + 
                ' -Dsonar.cobol.copy.suffixes=cpy' +
                ' -Dsonar.tests="' + pConfig.tttFolder + '"' +
                ' -Dsonar.testExecutionReportPaths="' + sonarResults + '"' +
                ' -Dsonar.coverageReportPaths=' + './Coverage/CodeCoverage.xml' +
                ' -Dsonar.ws.timeout=240' +
                ' -Dsonar.sourceEncoding=UTF-8'

        }
    }
}
