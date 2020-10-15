#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL
import groovy.xml.XmlSlurper

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

def initialize(){

    CC_TEST_ID_MAX_LEN      = 15

    executionGitBranch      = BRANCH_NAME
    sharedLibName           = 'RNU_Shared_Lib'
    synchConfigFile         = './git2ispw/synchronizationconfig.yml'
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
    sonarCodeCoverageFile   = './Coverage/CodeCoverage.xml'
    jUnitResultsFile        = './TTTUnit/generated.cli.suite.junit.xml

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
    def tmpWorkspace = workspace.replace('\\', '/')

    tttConfigFolder = '..' + tmpWorkspace.substring(tmpWorkspace.lastIndexOf('/')) + '@libs/' + sharedLibName + '/resources' + '/' + synchConfig.tttConfigFolder

    //*********************************************************************************
    // Build Code Coverage Test ID from current branch name and build number
    // The test id must not be londer than 15 characters
    //*********************************************************************************
    ccTestId = executionGitBranch.substring(executionGitBranch.length() - (CC_TEST_ID_MAX_LEN - BUILD_NUMBER.length() - 1)) + '_' + BUILD_NUMBER
}

def getSonarResults(){
    def resultsList         = ''
    def resultsFileContent  = readFile(file: sonarResultsFile)
    def resultsXmlText      = new XmlSlurper().parseText(resultsFileContent)
    
    resultsXmlText.testExecutions.file.each {
        resultsList = resultsList + it.@path.replace('.result', '.sonar.xml') + ','
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
                currentBuild.result = 'SUCCESS'
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

        stage('Execute Tests') {

            totaltest(
                serverUrl:                          synchConfig.cesUrl, 
                credentialsId:                      pipelineParms.hostCredentialsId, 
                environmentId:                      synchConfig.tttVtEnvironmentId, //synchConfig.tttNvtEnvironmentId, 
                localConfig:                        true, //false,
                localConfigLocation:                tttConfigFolder, 
                folderPath:                         synchConfig.tttRootFolder + '/' + synchConfig.tttVtFolder, //synchConfig.tttNvtFolder, 
                recursive:                          true, 
                selectProgramsOption:               true, 
                jsonFile:                           changedProgramsFile,
                haltPipelineOnFailure:              false,                 
                stopIfTestFailsOrThresholdReached:  false,
                collectCodeCoverage:                true,
                collectCCRepository:                pipelineParms.ccRepo,
                collectCCSystem:                    ispwConfig.ispwApplication.application,
                collectCCTestID:                    ccTestId,
                clearCodeCoverage:                  false,
                ccThreshold:                        pipelineParms.ccThreshold,     
                logLevel:                           'INFO'
            )

            step([
                $class:             'CodeCoverageBuilder', 
                connectionId:       pipelineParms.hciConnectionId, 
                credentialsId:      pipelineParms.hostCredentialsId,
                analysisProperties: """
                    cc.sources=${synchConfig.ccSources}
                    cc.repos=${pipelineParms.ccRepo}
                    cc.system=${ispwConfig.ispwApplication.application}
                    cc.test=${ccTestId}
                    cc.ddio.overrides=${ccDdioOverrides}
                """
             ])

            junit allowEmptyResults: true, testResults: jUnitResultsFile

        }

        stage("SonarQube Scan") {

            def scannerHome           = tool synchConfig.sonarScanner

            sonarResults = getSonarResults()

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
                ' -Dsonar.sourceEncoding=UTF-8'

            }
        }   

    }
}