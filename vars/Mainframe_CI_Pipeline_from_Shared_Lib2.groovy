#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
String  configFile
String  mailListFile
String  tttRepo
def call(Map pipelineParams)
{
    configFile          = 'pipelineConfig.yml'
    mailListFile        = 'mailList.yml'
    def pipelineConfig  = readYaml(text: libraryResource(configFile))
    def mailList        = readYaml(text: libraryResource(mailListFile))
    def pathNum         = pipelineParams.ispwSrcLevel.charAt(pipelineParams.ispwSrcLevel.length() - 1)
    def ispwTargetLevel = "QA" + pathNum
    def mailRecipient   = mailList[(pipelineParams.ispwOwner.toUpperCase())]
    def ccDdioOverride  = "SALESSUP.${pipelineParams.ispwApplication}.${ispwTargetLevel}.LOAD.SSD"
    node
    {
        dir("./") 
        {
            deleteDir()
        }
        stage("Retrieve Code From ISPW")
        {
            checkout([$class: 'IspwContainerConfiguration', connectionId: "${pipelineParams.hciConnectionId}", credentialsId: "${pipelineParams.hciToken}", componentType: '', containerName: pipelineParams.ispwContainer, containerType: pipelineParams.ispwContainerType, ispwDownloadAll: false, ispwDownloadIncl: true, serverConfig: '', serverLevel: ispwTargetLevel])
        }
        stage("Retrieve Tests")
        {
            echo "Checking out Branch " + pipelineConfig.git.branch
            def gitFullUrl = "${pipelineConfig.git.url}/${pipelineParams.gitProject}/${pipelineParams.ispwStream}_${pipelineParams.ispwApplication}${pipelineConfig.git.tttRepoExtension}"
            checkout(changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: "*/${pipelineConfig.git.branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${pipelineConfig.ttt.folder}"]], submoduleCfg: [], [[credentialsId: "${pipelineParams.gitCredentials}", name: 'origin', url: "${gitFullUrl}"]]])
        }
        stage("Execute related Unit Tests")
        {
            totaltest(serverUrl: pipelineConfig.ces.url, serverCredentialsId: pipelineParams.hciToken, credentialsId: pipelineParams.hciToken, environmentId: pipelineConfig.ttt.vtEnvironment, localConfig: false, folderPath: pipelineConfig.ttt.folder, recursive: true, selectProgramsOption: true, jsonFile: pipelineConfig.ispw.changedProgramsFile, haltPipelineOnFailure: false, stopIfTestFailsOrThresholdReached: false, createJUnitReport: true, createReport: true, createResult: true, createSonarReport:                  true, contextVariables:                   '"ispw_app=' + pipelineParams.ispwApplication + ',ispw_level=' + ispwTargetLevel + '"', collectCodeCoverage:                true, collectCCRepository:                pipelineParams.ccRepository, collectCCSystem:                    pipelineParams.ispwApplication, collectCCTestID:                    BUILD_NUMBER, clearCodeCoverage:                  false, logLevel:                           'INFO')
            junit allowEmptyResults: true, keepLongStdio: true, testResults: "TTTUnit/*.xml"
        }
        stage("Collect Coverage Metrics")
        {
            def String ccSources="${pipelineParams.ispwApplication}\\${pipelineConfig.ispw.mfSourceFolder}"
            def ccproperties = 'cc.sources=' + ccSources + '\rcc.repos=' + pipelineParams.ccRepository + '\rcc.system=' + pipelineParams.ispwApplication  + '\rcc.test=' + BUILD_NUMBER + '\rcc.ddio.overrides=' + ccDdioOverride
            step([$class:                 'CodeCoverageBuilder', connectionId:           pipelineParams.hciConnectionId, credentialsId:          pipelineParams.hciToken, analysisProperties:     ccproperties])
        }
        stage("Check SonarQube Quality Gate") 
        {
            def scannerHome = tool pipelineConfig.sq.scannerName
            withSonarQubeEnv(pipelineConfig.sq.serverName)       
            {
                bat "${scannerHome}/bin/sonar-scanner -Dsonar.tests=${pipelineConfig.ttt.folder} -Dsonar.testExecutionReportPaths=${pipelineConfig.ttt.sonarResultsFile} -Dsonar.coverageReportPaths=Coverage/CodeCoverage.xml -Dsonar.projectKey=${JOB_NAME} -Dsonar.projectName=${JOB_NAME} -Dsonar.projectVersion=1.0 -Dsonar.sources=${pipelineParams.ispwApplication}/${pipelineConfig.ispw.mfSourceFolder} -Dsonar.cobol.copy.directories=${pipelineParams.ispwApplication}/${pipelineConfig.ispw.mfSourceFolder} -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,results,scenario -Dsonar.cobol.copy.suffixes=cpy -Dsonar.sourceEncoding=UTF-8"
            }
            timeout(time: 2, unit: 'MINUTES') {
                def qg = waitForQualityGate()
                if (qg.status != 'OK')
                {
                    echo "Sonar quality gate failure: ${qg.status}"
                    echo "Pipeline will be aborted and ISPW Assignment will be regressed"
                    echo "Regress Assignment ${pipelineParams.ispwContainer}, Level ${ispwTargetLevel}"
                    ispwOperation(connectionId:           pipelineParams.hciConnectionId, credentialsId:          pipelineParams.jenkinsCesToken, consoleLogResponseBody: true, ispwAction:             'RegressAssignment', ispwRequestBody:        """\nassignmentId=${pipelineParams.ispwContainer}\nlevel=${ispwTargetLevel}\n""")
                    currentBuild.result = "FAILURE"
                    emailext(subject:    '$DEFAULT_SUBJECT', body:       '$DEFAULT_CONTENT', replyTo:    '$DEFAULT_REPLYTO',to:         "${mailRecipient}")
                    error "Exiting Pipeline"
                }
            }   
        }
        stage("Start release in XL Release")
        {
            xlrCreateRelease(releaseTitle:       'A Release for $BUILD_TAG', serverCredentials:  "${pipelineConfig.xlr.user}", startRelease:       true, template:           "${pipelineConfig.xlr.template}", variables:          [[propertyName: 'ISPW_Dev_level',    propertyValue: "${ispwTargetLevel}"], [propertyName: 'ISPW_RELEASE_ID',   propertyValue: "${pipelineParams.ispwRelease}"], [propertyName: 'CES_Token',         propertyValue: "${pipelineParams.cesToken}"]])
        }
    }
}