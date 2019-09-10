#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/
PipelineConfig  pConfig
GitHelper       gitHelper
IspwHelper      ispwHelper
TttHelper       tttHelper
SonarHelper     sonarHelper 
XlrHelper       xlrHelper

String          mailMessageExtension
String          cesToken
def             componentList
def             listOfExecutedTargets
def             programStatusList
def             pipelinePass

def initialize(pipelineParams)
{
    pipelinePass = true

    // Clean out any previously downloaded source
    dir(".\\") 
    {
        deleteDir()
    }

    def mailListlines
    /* Read list of mailaddresses from "private" Config File */
    /* The configFileProvider creates a temporary file on disk and returns its path as variable */
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

    pConfig     = new   PipelineConfig(
                            steps, 
                            workspace,
                            pipelineParams,
                            mailListlines
                        )

    pConfig.initialize()                                            

    gitHelper   = new   GitHelper(
                            steps
                        )

    withCredentials([usernamePassword(credentialsId: "${pConfig.gitCredentials}", passwordVariable: 'gitPassword', usernameVariable: 'gitUsername')]) 
    {
        gitHelper.initialize(gitPassword, gitUsername, pConfig.ispwOwner, pConfig.mailRecipient)
    }

    withCredentials(
        [string(credentialsId: "${pConfig.cesTokenId}", variable: 'cesTokenTemp')]
    ) 
    {
        cesToken = cesTokenTemp
    }

    ispwHelper  = new   IspwHelper(
                            steps, 
                            pConfig
                        )

    componentList           = ispwHelper.getComponents(cesToken, pConfig.ispwContainer, pConfig.ispwContainerType)

    componentList.each
    {
        componentStatusList[it]['sourceStatus'] = 'UNKNOWN'
        componentStatusList[it]['utStatus']     = 'UNKNOWN'
        componentStatusList[it]['ftStatus']     = 'UNKNOWN'
    }

    tttHelper   = new   TttHelper(
                            this,
                            steps,
                            pConfig
                        )

    sonarHelper = new SonarHelper(this, steps, pConfig)
    sonarHelper.initialize()

    mailMessageExtension    = ''
    programStatusList       = [:]
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

            mailMessageExtension = mailMessageExtension + "\n\nINITIAL SOURCE SCAN RESULTS\n"

            componentStatusList = sonarHelper.scanSources(componentList, componentStatusList)
            
            componentStatusList.each
            {
                if (it.value == 'FAIL')
                {
                    echo "Sonar quality gate failure: ${sonarGateResult} \nfor program ${it}"

                    mailMessageExtension = mailMessageExtension +
                        "\nGenerated code for program ${it} FAILED the Quality gate ${sonarGate}. \n\nTo review results\n" +
                        "SonarQube dashboard : ${pConfig.sqServerUrl}/dashboard?id=${sonarProjectName}"

                    componentList.remove(it)
                    pipelinePass = false
                }
                else
                {
                    mailMessageExtension = mailMessageExtension +
                        "\nGenerated code for program ${it} PASSED the Quality gate ${sonarGate}. \n\n" +
                        "SonarQube results may be reviewed at ${pConfig.sqServerUrl}/dashboard?id=${sonarProjectName}\n\n"
                }   
            }
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
            def sonarProjectName
            mailMessageExtension    = mailMessageExtension + "\n\nUNIT TEST RESULTS\n"

            componentStatusList = sonarHelper.scanUt(componentList, componentStatusList, listOfExecutedTargets)

            componentStatusList.each
            {
                if (it.value == 'FAIL')
                {
                    echo "Sonar quality gate failure: ${sonarGateResult} for program ${it}"

                    mailMessageExtension = mailMessageExtension +
                        "\nGenerated code for program ${it} FAILED the Quality gate ${sonarGate}. \n\nTo review results\n" +
                        "JUnit reports       : ${BUILD_URL}/testReport/ \n\n" +
                        "SonarQube dashboard : ${pConfig.sqServerUrl}/dashboard?id=${sonarProjectName}"

                    componentList.remove[it]
                    pipelinePass = false
                }
                else
                {
                    mailMessageExtension = mailMessageExtension +
                        "\nGenerated code for program ${it} PASSED the Quality gate ${sonarGate}. \n\n" +
                        "SonarQube results may be reviewed at ${pConfig.sqServerUrl}/dashboard?id=${sonarProjectName}\n\n"
                } 
            }

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
                mailMessageExtension = mailMessageExtension +
                    "\n\n\nFINAL RESULTS\n\nInitial scans or unit tests failed. The pipeline will be aborted, and the following components will be regressed: \n\n"

                componentStatusList.each
                {
                    if(
                        it.value['sourceStatus']    == 'FAIL' ||
                        it.value['utStatus']        == 'FAIL'
                    )

                    ispwHelper.regressTask(it, cesToken)
                    mailMessageExtension = mailMessageExtension +
                        it + "\n"
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
    //return [pipelineResult: currentBuild.result, pipelineMailText: mailMessageExtension, pipelineConfig: pConfig, pipelineProgramStatusList: programStatusList]
}