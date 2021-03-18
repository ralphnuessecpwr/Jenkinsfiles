#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL

/**
 This is an example Jenkins Pipeline Script that runs a CI process against COBOL Code using Jenkins Shared Libraries.  
 As the basic example, this pipeline is designed to be triggered from ISPW 
 on the promotion of code from a Test level in a controlled level.  The pipeline runs a series of quality checks on the 
 promoted code to ensure that it meets the quality standards that an organization defined in SonarQube.
 
 This Pipeline uses the following Jenkins Plugins
 Compuware Common Configuration Plugin - https://plugins.jenkins.io/compuware-common-configuration
 Compuware Source Code Download for Endevor, PDS, and ISPW Plugin - https://plugins.jenkins.io/compuware-scm-downloader
 Compuware Topaz for Total Test Plugin - https://plugins.jenkins.io/compuware-topaz-for-total-test
 Compuware Xpediter Code Coverage Plugin - https://plugins.jenkins.io/compuware-xpediter-code-coverage
 Pipeline Utilities Plugin - https://plugins.jenkins.io/pipeline-utility-steps
 SonarQube Scanner Plugin - https://plugins.jenkins.io/sonar
 XebiaLabs XL Release Plugin - https://plugins.jenkins.io/xlrelease-plugin
 
 This Pipeline Requires the below Parameters to be defined in the Jenkins Job
 The Jenkins Parameters can be supplied by a ISPW webhook by defining a webhook like the example below.  
 Please note that the assignment is not currently available in the webhook, but will be added in a future release.
 http://<<your jenkins server>>/job/<<your jenkins job>>/buildWithParameters?ISPW_Stream=$$stream$$&pipelineParams.ispwContainer=$$release$$&pipelineParams.ispwSrcLevel=$$level$$&SetId=$$setID$$&ISPW_Release=$$release$$&Owner=$$owner$$
 
 ISPW Webhook Parameter List, these parameters need to be defined in the Jenkins job configuration and will be passed by the ISPW Webhook
 @param ISPW_Stream - ISPW Stream that had the code promotion
 @param ISPW_Application - ISPW Application that had the code promotion
 @param ISPW_Container - ISPW Container that had the code promotion
 @param ISPW_Container_Type - Type of ISPW Container that had the code promotion, 0 - Assignment, 1 - Release, 3 - Set
 @param ISPW_Release - The ISPW Release Value that will be passed to XL Release
 @param ISPW_Src_Level - ISPW Level that code was promoted from
 @param ISPW_Owner - The ISPW Owner value from the ISPW Set that was created for the promotion

 The script or jenkinsfile defined in the job configuration needs to call this pipeline and pass the parameters above in a Map:

 ispwStream:        ISPW_Stream,
 ispwApplication:   ISPW_Application,
 ispwContainer:     ISPW_Container,
 ispwContainerType: ISPW_Container_Type,
 ispwRelease:       ISPW_Release,
 ispwSrcLevel:      ISPW_Src_Level,
 ispwOwner:         ISPW_Owner,

 In addition to the parameters passed via Webhook, the pipeline also takes the following parameters from the call, which need to extend the map. 
 These parameters may differ between differennt applications/instances of the job implemented by the pipeline.
 cesToken:          <CES_Token>,            CES Personal Access Token.  These are configured in Compuware Enterprise Services / Security / Personal Access Tokens 
 jenkinsCesToken:   <Jenkins_CES_Token>     Jenkins Credentials ID for the CES Personal Access Token
 hciConnectionId:   <HCI_Conn_ID>           HCI Connection ID configured in the Compuware Common Configuration Plugin.  Use Pipeline Syntax Generator to determine this value. 
 hciToken:          <HCI_Token>             The ID of the Jenkins Credential for the TSO ID that will used to execute the pipeline
 ccRepository:      <CoCo_Repository>       The Compuware Xpediter Code Coverage Repository that the Pipeline will use
 gitProject:        <Git_Project>           Github project/user used to store the Topaz for Total Test Projects
 gitCredentials:    <Git_Credentials>       Jenkins credentials for logging into git 
*/

/**
 In the basic example there were parameters hard coded into the pipeline. These would be setting that apply to any instance of the pipeline. Instad of hardcoding, 
 we make use of the Shared Library resource folder, which may store configuration files, and we read the configuration from those file. This example pipeline assumes the
 configuration stired as .yml file.

 @param pipelineConfig.git.url                   - Url that will be used in various git commands
 @param pipelineConfig.git.tttRepo               - Git repo that contains Topaz for Total Test Projects
 @param pipelineConfig.git.branch                - Git branch to be used by the pipeline
 @param pipelineConfig.sq.scannerName            - Name of SonarQube Scanner installation in "Manage Jenkins" -> "Global Tool Configuration" -> "SonarQube Scanner Installations"
 @param pipelineConfig.sq.serverName             - Name of SonarQube Server in "Manage Jenkins" -> "Configure System" -> "Sonar Qube servers"
 @param pipelineConfig.xlr.template              - XL Release template to trigger at the end of the Jenkins workflow
 @param pipelineConfig.xlr.user                  - XL Release user ID. Configured in Jenkins/Manage Jenkins/Configure System/XL Release credentials
 @param pipelineConfig.ttt.folder                - Folder to download TTT projects from GitHub into, i.e. store all TTT projects into one folder
 @param pipelineConfig.ttt.vtEnvironment         - ID of a valid batch execution environment within the Topatz for Total Test repository (or local environment if local configuration is used for TTT)
 @param pipelineConfig.ces.url                   - URL to the ISPW Rest API
 @param pipelineConfig.ispw.runtime              - ISPW Runtime configuration
 @param pipelineConfig.ispw.changedProgramsFile  - Json file containing list of compnents affected by an ISPW operation. Will be generated by ISPW plugins, automatically. 
 @param pipelineConfig.ispw.mfSourceFolder       - directory that contains cobol source downloaded from ISPW
*/

String  configFile
String  mailListFile
String  tttRepo

/**
Call method to execute the pipeline from a shared library
@param pipelineParams - Map of paramter/value pairs
*/
def call(Map pipelineParams)
{
    configFile          = 'pipelineConfig.yml'
    mailListFile        = 'mailList.yml'

    //*********************************************************************************
    // Read pipelineConfig.yml and mailList.yml from Shared Library resources folder as
    // yaml document.
    //*********************************************************************************
    def pipelineConfig  = readYaml(text: libraryResource(configFile))
    def mailList        = readYaml(text: libraryResource(mailListFile))

    // Determine the current ISPW Path and Level that the code Promotion is from
    def pathNum         = pipelineParams.ispwSrcLevel.charAt(pipelineParams.ispwSrcLevel.length() - 1)
    
    // Also set the Level that the code currently resides in
    def ispwTargetLevel = "QA" + pathNum

    def mailRecipient   = mailList[(pipelineParams.ispwOwner.toUpperCase())]

    def ccDdioOverride  = "SALESSUP.${pipelineParams.ispwApplication}.${ispwTargetLevel}.LOAD.SSD"

    node
    {
        // Clean out any previously downloaded source
        dir("./") 
        {
            deleteDir()
        }

        /*
        This stage is used to retrieve source from ISPW
        */ 
        stage("Retrieve Code From ISPW")
        {
                checkout(
                    [
                        $class:             'IspwContainerConfiguration', 
                        connectionId:       "${pipelineParams.hciConnectionId}",
                        credentialsId:      "${pipelineParams.hciToken}", 
                        componentType:      '', 
                        containerName:      pipelineParams.ispwContainer, 
                        containerType:      pipelineParams.ispwContainerType, 
                        ispwDownloadAll:    false, 
                        ispwDownloadIncl:   true, 
                        serverConfig:       '', 
                        serverLevel:        ispwTargetLevel
                    ]
                )
        }
        
        /* 
        This stage is used to retrieve Topaz for Total Tests from Git
        */ 
        stage("Retrieve Tests")
        {
            echo "Checking out Branch " + pipelineConfig.git.branch

            //Retrieve the Tests from Github that match that ISPW Stream and Application
            def gitFullUrl = "${pipelineConfig.git.url}/${pipelineParams.gitProject}/${pipelineParams.ispwStream}_${pipelineParams.ispwApplication}${pipelineConfig.git.tttRepoExtension}"

            checkout(
                changelog:  false, 
                poll:       false, 
                scm:        [
                    $class:                             'GitSCM', 
                    branches:                           [[
                        name: "*/${pipelineConfig.git.branch}"
                        ]], 
                    doGenerateSubmoduleConfigurations:  false, 
                    extensions:                         [[
                        $class:             'RelativeTargetDirectory', 
                        relativeTargetDir:  "${pipelineConfig.ttt.folder}"
                    ]], 
                    submoduleCfg:                       [], 
                    userRemoteConfigs:                  [[
                        credentialsId:  "${pipelineParams.gitCredentials}", 
                        name:           'origin', 
                        url:            "${gitFullUrl}"
                    ]]
                ]
            )

        }

        stage("Execute related Unit Tests")
        {

            totaltest(
                serverUrl:                          pipelineConfig.ces.url, 
                serverCredentialsId:                pipelineParams.hciToken, 
                credentialsId:                      pipelineParams.hciToken, 
                environmentId:                      pipelineConfig.ttt.vtEnvironment,
                localConfig:                        false,              // If you are not using the TTT repository and use the local TotalTestConfiguration, set to true
                //localConfigLocation:                tttConfigFolder,  // and point to workspace folder containing the local TotalTestConfiguration
                folderPath:                         pipelineConfig.ttt.folder, 
                recursive:                          true, 
                selectProgramsOption:               true, 
                jsonFile:                           pipelineConfig.ispw.changedProgramsFile,
                haltPipelineOnFailure:              false,                 
                stopIfTestFailsOrThresholdReached:  false,
                createJUnitReport:                  true, 
                createReport:                       true, 
                createResult:                       true, 
                createSonarReport:                  true,
                contextVariables:                   '"ispw_app=' + pipelineParams.ispwApplication + ',ispw_level=' + ispwTargetLevel + '"',
                collectCodeCoverage:                true,
                collectCCRepository:                pipelineParams.ccRepository,
                collectCCSystem:                    pipelineParams.ispwApplication,
                collectCCTestID:                    BUILD_NUMBER,
                clearCodeCoverage:                  false,
                logLevel:                           'INFO'
            )

            // Process the Total Test Junit result files into Jenkins
            junit allowEmptyResults: true, keepLongStdio: true, testResults: "TTTUnit/*.xml"
        }

        /* 
        This stage retrieve Code Coverage metrics from Xpediter Code Coverage for the test executed in the Pipeline
        */ 
        stage("Collect Coverage Metrics")
        {
                // Code Coverage needs to match the code coverage metrics back to the source code in order for them to be loaded in SonarQube
                // The source variable is the location of the source that was downloaded from ISPW
                def String ccSources="${pipelineParams.ispwApplication}\\${pipelineConfig.ispw.mfSourceFolder}"

                // The Code Coverage Plugin passes it's primary configuration in the string or a file
                def ccproperties = 'cc.sources=' + ccSources + '\rcc.repos=' + pipelineParams.ccRepository + '\rcc.system=' + pipelineParams.ispwApplication  + '\rcc.test=' + BUILD_NUMBER + '\rcc.ddio.overrides=' + ccDdioOverride

                step(
                    [
                        $class:                 'CodeCoverageBuilder',                    
                        connectionId:           pipelineParams.hciConnectionId, 
                        credentialsId:          pipelineParams.hciToken,
                        analysisProperties:     ccproperties    // Pass in the analysisProperties as a string
                    ]
                )
        }

        /* 
        This stage pushes the Source Code, Test Metrics and Coverage metrics into SonarQube and then checks the status of the SonarQube Quality Gate.  
        If the SonarQube quality date fails, the Pipeline fails and stops
        */ 
        stage("Check SonarQube Quality Gate") 
        {
            // Retrieve the location of the SonarQube Scanner bin files  
            def scannerHome = tool pipelineConfig.sq.scannerName

            withSonarQubeEnv(pipelineConfig.sq.serverName)       // Name of the SonarQube server defined in Jenkins / Configure Systems / SonarQube server section
            {
                // Call the SonarQube Scanner with properties defined above
                bat "${scannerHome}/bin/sonar-scanner "                                                                         + 
                // Folder containing test definitions, i.e. TTT scenarios
                    " -Dsonar.tests=${pipelineConfig.ttt.folder}"                                                               +
                // File (or list of files) containing test results in Sonar format                    
                    " -Dsonar.testExecutionReportPaths=${pipelineConfig.ttt.sonarResultsFile}"                                  +
                // File containing Code Coverage results in Sonar format
                    " -Dsonar.coverageReportPaths=Coverage/CodeCoverage.xml"                                                    +
                // Sonar project key to use/create
                    " -Dsonar.projectKey=${JOB_NAME}"                                                                           +
                // Sonar project name to use/create
                    " -Dsonar.projectName=${JOB_NAME}"                                                                          +
                    " -Dsonar.projectVersion=1.0"                                                                               +
                // Folder containing the (mainframe) sources to analyze
                    " -Dsonar.sources=${pipelineParams.ispwApplication}/${pipelineConfig.ispw.mfSourceFolder}"                  +
                // Folder containing the (mainframe) copybooks
                    " -Dsonar.cobol.copy.directories=${pipelineParams.ispwApplication}/${pipelineConfig.ispw.mfSourceFolder}"   + 
                // File extensions Sonar is supposed to recognize for "sources". The list also needs to contain any TTT related extensions                    
                    " -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,results,scenario"                             +
                // File extensions Sonar is supposed to recognize for "copybooks"                   
                    " -Dsonar.cobol.copy.suffixes=cpy"                                                                          +
                    " -Dsonar.sourceEncoding=UTF-8"
            }
            
            // Wait for the results of the SonarQube Quality Gate
            timeout(time: 2, unit: 'MINUTES') {
                
                // Wait for webhook call back from SonarQube.  SonarQube webhook for callback to Jenkins must be configured on the SonarQube server.
                def qg = waitForQualityGate()
                
                // Evaluate the status of the Quality Gate
                if (qg.status != 'OK')
                {
                    echo "Sonar quality gate failure: ${qg.status}"
                    echo "Pipeline will be aborted and ISPW Assignment will be regressed"

                    echo "Regress Assignment ${pipelineParams.ispwContainer}, Level ${ispwTargetLevel}"

                    ispwOperation(
                        connectionId:           pipelineParams.hciConnectionId, 
                        credentialsId:          pipelineParams.cesToken,
                        consoleLogResponseBody: true,  
                        ispwAction:             'RegressAssignment', 
                        ispwRequestBody:        """
                            runtimeConfiguration=${pipelineConfig.ispw.runtime}
                            assignmentId=${pipelineParams.ispwContainer}
                            level=${ispwTargetLevel}
                            """
                    )

                    currentBuild.result = "FAILURE"

                    // Email
                    emailext(
                        subject:    '$DEFAULT_SUBJECT',
                        body:       '$DEFAULT_CONTENT',
                        replyTo:    '$DEFAULT_REPLYTO',
                        to:         "${mailRecipient}"
                    )
                    
                    error "Exiting Pipeline" // Exit the pipeline with an error if the SonarQube Quality Gate is failing
                }
            }   
        }

        /* 
        This stage triggers a XL Release Pipeline that will move code into the high levels in the ISPW Lifecycle  
        */ 
        stage("Start release in XL Release")
        {
            // Trigger XL Release Jenkins Plugin to kickoff a Release
            xlrCreateRelease(
                releaseTitle:       'A Release for $BUILD_TAG',
                serverCredentials:  "${pipelineConfig.xlr.user}",
                startRelease:       true,
                template:           "${pipelineConfig.xlr.template}",
                variables:          [
                    [propertyName: 'ISPW_Dev_level',    propertyValue: "${ispwTargetLevel}"], // Level in ISPW that the Code resides currently
                    [propertyName: 'ISPW_RELEASE_ID',   propertyValue: "${ISPW_Release}"],     // ISPW Release value from the ISPW Webhook
                    [propertyName: 'CES_Token',         propertyValue: "${CES_Token}"]
                ]
            )
        }
    }
}