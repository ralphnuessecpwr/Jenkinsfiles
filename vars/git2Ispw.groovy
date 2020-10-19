#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL
import groovy.xml.*

String executionGitBranch      
String sharedLibName           
String synchConfigFolder       
String synchConfigFile         
String ispwConfigFile      
String automaticBuildFile  
String changedProgramsFile 
String branchMappingString     
String tttConfigFolder         
String tttVtExecutionLoad    
String tttUtJclSkeletonFile  
String ccDdioOverrides         
String sonarCobolFolder        
String sonarCopybookFolder     
String sonarResultsFile        
String sonarCodeCoverageFile   
String jUnitResultsFile

def branchMapping             
def ispwConfig
def synchConfig
def automaticBuildInfo
def executionMapRule
def programList
def tttProjectList

def CC_TEST_ID_MAX_LEN
def CC_SYSTEM_ID_MAX_LEN

def initialize(){

    CC_TEST_ID_MAX_LEN      = 15
    CC_SYSTEM_ID_MAX_LEN    = 15

    executionGitBranch      = BRANCH_NAME
    sharedLibName           = 'RNU_Shared_Lib'
    synchConfigFile         = './git2ispw/synchronization.yml'
    ispwConfigFile          = './ispwconfig.yml'
    automaticBuildFile      = './automaticBuildParams.txt'
    changedProgramsFile     = './changedPrograms.json'
    branchMappingString     = ''    
    tttConfigFolder         = ''
    tttVtExecutionLoad      = ''
    ccDdioOverrides         = ''
    sonarCobolFolder        = './MainframeSources/Cobol/Programs'
    sonarCopybookFolder     = './MainframeSources/Cobol/Copybooks'
    sonarResultsFile        = './TTTSonar/generated.cli.suite.sonar.xml'
    sonarResultsFileUT      = './TTTSonar/generated.cli.UT.suite.sonar.xml'
    sonarCodeCoverageFile   = './Coverage/CodeCoverage.xml'
    jUnitResultsFile        = './TTTUnit/generated.cli.suite.junit.xml'

    //*********************************************************************************
    // Read ispwconfig.yml
    // Strip the first line of ispwconfig.yml because readYaml can't handle the !! tag
    //*********************************************************************************
    def tmpText     = readFile(file: ispwConfigFile)

    // remove the first line (i.e. the substring following the first carriage return '\n')
    tmpText         = tmpText.substring(tmpText.indexOf('\n') + 1)

    // convert the text to yaml
    ispwConfig      = readYaml(text: tmpText)

    //*********************************************************************************
    // Read synchconfig.yml from Shared Library resources folder
    //*********************************************************************************
    def fileText    = libraryResource synchConfigFile
    
    synchConfig     = readYaml(text: fileText)

    //*********************************************************************************
    // Build branch mapping string to be used as parameter in the gitToIspwIntegration
    // Build load library name from configuration, replacing application marker by actual name
    //*********************************************************************************
    synchConfig.branchInfo.each {

        branchMappingString = branchMappingString + it.key + '** => ' + it.value.ispwBranch + ',' + it.value.mapRule + '\n'

        if(executionGitBranch.contains(it.key)) {
            tttVtExecutionLoad = it.value.loadLib.replace('<ispwApplication>', ispwConfig.ispwApplication.application)
        }
    }

    //*********************************************************************************
    // If load library name is empty the branch name could not be mapped
    //*********************************************************************************
    if(tttVtExecutionLoad == ''){
        error "No branch mapping for branch ${executionGitBranch} was found. Execution will be aborted.\n" +
            "Correct the branch name to reflect naming conventions."
    }

    //*********************************************************************************
    // Build DDIO override parameter for Code Coverage, replacing application marker by actual name
    //*********************************************************************************
    synchConfig.ccDdioOverrides.each {
        ccDdioOverrides = ccDdioOverrides + it.toString().replace('<ispwApplication>', ispwConfig.ispwApplication.application)
    }

    //*********************************************************************************
    // The .tttcfg file and JCL skeleton are located in the pipeline shared library, resources folder
    // Determine path relative to current workspace
    //*********************************************************************************
    def tmpWorkspace        = workspace.replace('\\', '/')
    tttConfigFolder         = '..' + tmpWorkspace.substring(tmpWorkspace.lastIndexOf('/')) + '@libs/' + sharedLibName + '/resources' + '/' + synchConfig.tttConfigFolder
    tttUtJclSkeletonFile    = tttConfigFolder + '/JCLSkeletons/TTTRUNNER.jcl' 

    //*********************************************************************************
    // Build Code Coverage System ID from current branch, System ID must not be longer than 15 characters
    // Build Code Coverage Test ID from Build Number
    //*********************************************************************************
    if(executionGitBranch.length() > CC_SYSTEM_ID_MAX_LEN) {
        ccSystemId  = executionGitBranch.substring(executionGitBranch.length() - CC_SYSTEM_ID_MAX_LEN)
    }
    else {
        ccSystemId  = executionGitBranch
    }
    
    ccTestId    = BUILD_NUMBER
}

def setVtLoadlibrary(){

    def jclSkeleton = readFile(tttUtJclSkeletonFile).toString().replace('${loadlibraries}', tttVtExecutionLoad)

    writeFile(
        file:   tttUtJclSkeletonFile,
        text:   jclSkeleton
    )    

}

def getSonarResults(resultsFile){

    def resultsList         = ''
    def resultsFileContent  = readFile(file: resultsFile)
    resultsFileContent      = resultsFileContent.substring(resultsFileContent.indexOf('\n') + 1)
    def testExecutions      = new XmlSlurper().parseText(resultsFileContent)

    testExecutions.file.each {

        resultsList = resultsList + it.@path.toString().replace('.result', '.sonar.xml') + ','

    }

    return resultsList
}

def call(Map pipelineParms){

    //**********************************************************************
    // Start of Script
    //**********************************************************************
    node {
        stage ('Checkout and initialize') {
            // Clear workspace
            dir('./') {
                deleteDir()
            }

            checkout scm

            initialize()

            setVtLoadlibrary()

        }

        stage('Load code to mainframe') {

            try {

                gitToIspwIntegration( 
                    connectionId:       pipelineParms.hciConnectionId,                    
                    credentialsId:      pipelineParms.hostCredentialsId,                     
                    runtimeConfig:      ispwConfig.ispwApplication.runtimeConfig,
                    stream:             ispwConfig.ispwApplication.stream,
                    app:                ispwConfig.ispwApplication.application, 
                    branchMapping:      branchMappingString,
                    ispwConfigPath:     ispwConfigFile, 
                    gitCredentialsId:   pipelineParms.gitCredentialsId, 
                    gitRepoUrl:         pipelineParms.gitRepoUrl
                )

            }
            catch(Exception e) {

                echo "No Synchronisation to the mainframe.\n"
                echo e.toString()
                currentBuild.result = 'FAILURE'
                return

            }

        }

        // If the automaticBuildParams.txt has not been created, it means no programs
        // have been changed and the pipeline was triggered for other changes (in configuration files)
        // These changes do not need to be "built".
        try {
            automaticBuildInfo = readJSON(file: automaticBuildFile)
        }
        catch(Exception e) {

            echo "No Automatic Build Params file was found.\n" +
            "Meaning, no programs have been changed.\n" +
            "Job gets ended prematurely, but successfully."
            currentBuild.result = 'SUCCESS'
            return

        }

        stage('Build mainframe code') {

            ispwOperation(
                connectionId:           pipelineParms.hciConnectionId, 
                credentialsId:          pipelineParms.cesCredentialsId,       
                consoleLogResponseBody: true, 
                ispwAction:             'BuildTask', 
                ispwRequestBody:        '''buildautomatically = true'''
            )

        }

        stage('Execute Unit Tests') {

            totaltest(
                serverUrl:                          synchConfig.cesUrl, 
                credentialsId:                      pipelineParms.hostCredentialsId, 
                environmentId:                      synchConfig.tttVtEnvironmentId,
                localConfig:                        true, 
                localConfigLocation:                tttConfigFolder, 
                folderPath:                         synchConfig.tttRootFolder + '/' + synchConfig.tttVtFolder, 
                recursive:                          true, 
                selectProgramsOption:               true, 
                jsonFile:                           changedProgramsFile,
                haltPipelineOnFailure:              false,                 
                stopIfTestFailsOrThresholdReached:  false,
                collectCodeCoverage:                true,
                collectCCRepository:                pipelineParms.ccRepo,
                collectCCSystem:                    ccSystemId,
                collectCCTestID:                    ccTestId,
                clearCodeCoverage:                  false,
                ccThreshold:                        pipelineParms.ccThreshold,     
                logLevel:                           'INFO'
            )

        }

        bat 'ren ' + sonarResultsFile + ' ' + sonarResultsFileUT

        if(pipelineParms.branchType == 'master') {

            stage('Execute Module Integration Tests') {

                totaltest(
                    serverUrl:                          synchConfig.cesUrl, 
                    credentialsId:                      pipelineParms.hostCredentialsId, 
                    environmentId:                      synchConfig.tttNvtEnvironmentId, 
                    localConfig:                        false,
                    localConfigLocation:                tttConfigFolder, 
                    folderPath:                         synchConfig.tttRootFolder + '/' + synchConfig.tttNvtFolder, 
                    recursive:                          true, 
                    selectProgramsOption:               true, 
                    jsonFile:                           changedProgramsFile,
                    haltPipelineOnFailure:              false,                 
                    stopIfTestFailsOrThresholdReached:  false,
                    collectCodeCoverage:                true,
                    collectCCRepository:                pipelineParms.ccRepo,
                    collectCCSystem:                    ccSystemId,
                    collectCCTestID:                    ccTestId,
                    clearCodeCoverage:                  false,
                    ccThreshold:                        pipelineParms.ccThreshold,     
                    logLevel:                           'INFO'
                )
            }
        }

        step([
            $class:             'CodeCoverageBuilder', 
            connectionId:       pipelineParms.hciConnectionId, 
            credentialsId:      pipelineParms.hostCredentialsId,
            analysisProperties: """
                cc.sources=${synchConfig.ccSources}
                cc.repos=${pipelineParms.ccRepo}
                cc.system=${ccSystemId}
                cc.test=${ccTestId}
                cc.ddio.overrides=${ccDdioOverrides}
            """
        ])
            
        stage("SonarQube Scan") {

            def scannerHome = tool synchConfig.sonarScanner
            sonarResults    = getSonarResults(sonarResultsFileUT)

            withSonarQubeEnv(synchConfig.sonarServer) {

                bat '"' + scannerHome + '/bin/sonar-scanner"' + 
                ' -Dsonar.branch.name=' + executionGitBranch +
                ' -Dsonar.projectKey=' + ispwConfig.ispwApplication.stream + '_' + ispwConfig.ispwApplication.application + 
                ' -Dsonar.projectName=' + ispwConfig.ispwApplication.stream + '_' + ispwConfig.ispwApplication.application +
                ' -Dsonar.projectVersion=1.0' +
                ' -Dsonar.sources=' + sonarCobolFolder + 
                ' -Dsonar.cobol.copy.directories=' + sonarCopybookFolder +
                ' -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,result' + 
                ' -Dsonar.cobol.copy.suffixes=cpy' +
                ' -Dsonar.tests="' + synchConfig.tttRootFolder + '"' +
                ' -Dsonar.testExecutionReportPaths="' + sonarResults + '"' +
                ' -Dsonar.coverageReportPaths=' + sonarCodeCoverageFile +
                ' -Dsonar.ws.timeout=240' +
                ' -Dsonar.sourceEncoding=UTF-8'

            }
        }   
    }
}