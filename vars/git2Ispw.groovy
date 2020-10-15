#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL

String ispwConfigFileName     
String synchConfigFolder      
String synchConfigFileName    
String automaticBuildFileName 
String changedProgramsFileName
String tttConfigFolder
String ccDdioOverrides        
String executionGitBranch     
String branchMappingString    
String sharedLibName
String ccTestId
String sonarCobolFolder
String sonarCopybookFolder

def branchMapping             
def ispwConfig
def synchConfig
def automaticBuildInfo
def executionMapRule
def programList
def tttProjectList

def CC_TEST_ID_MAX_LEN

def initialize(){

    ispwConfigFileName      = 'ispwconfig.yml'
    synchConfigFolder       = 'git2ispw'
    synchConfigFileName     = 'synchronizationconfig.yml'
    automaticBuildFileName  = 'automaticBuildParams.txt'
    changedProgramsFileName = 'changedPrograms.json'
    tttConfigFolder         = ''
    ccDdioOverrides         = ''
    executionGitBranch      = BRANCH_NAME
    branchMappingString     = ''
    tttVtExecutionLoad      = ''
    sharedLibName           = 'RNU_Shared_Lib'
    sonarCobolFolder        = 'MainframeSources/Cobol/Programs'
    sonarCopybookFolder     = 'MainframeSources/Cobol/Copybooks'

    CC_TEST_ID_MAX_LEN      = 15


    //*********************************************************************************
    // Read ispwconfig.yml
    // Strip the first line of ispwconfig.yml because readYaml can't handle the !! tag
    //*********************************************************************************
    def tmpText     = readFile(file: ispwConfigFileName)

    // remove the first line (i.e. the substring following the first carriage return '\n')
    tmpText         = tmpText.substring(tmpText.indexOf('\n') + 1)

    // convert the text to yaml
    ispwConfig      = readYaml(text: tmpText)

    //*********************************************************************************
    // Read synchconfig.yml
    //*********************************************************************************
    def filePath    = synchConfigFolder + '/' + synchConfigFileName
    def fileText    = libraryResource filePath
    
    synchConfig     = readYaml(text: fileText)

    synchConfig.branchInfo.each {

        branchMappingString = branchMappingString + it.key + '** => ' + it.value.ispwBranch + ',' + it.value.mapRule + '\n'

        if(executionGitBranch.contains(it.key)) {
            tttVtExecutionLoad = it.value.loadLib.replace('<ispwApplication>', ispwConfig.ispwApplication.application)
        }
    }

    if(tttVtExecutionLoad == ''){
        error "No branch mapping for branch ${executionGitBranch} was found. Execution will be aborted.\n" +
            "Correct the branch name to reflect naming conventions."
    }

    synchConfig.ccDdioOverrides.each {
        ccDdioOverrides = ccDdioOverrides + it.toString().replace('<ispwApplication>', ispwConfig.ispwApplication.application)
    }

    def tmpWorkspace = workspace.replace('\\', '/')

    tttConfigFolder = '..' + tmpWorkspace.substring(tmpWorkspace.lastIndexOf('/')) + '@libs/' + sharedLibName + '/resources' + '/' + synchConfig.tttConfigFolder

    ccTestId = executionGitBranch.substring(executionGitBranch.length() - (CC_TEST_ID_MAX_LEN - BUILD_NUMBER.length() - 1)) + '_' + BUILD_NUMBER
    echo "CC Test Id" 
    echo ccTestId
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
                    ispwConfigPath:     ispwConfigFileName, 
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
            automaticBuildInfo = readJSON(file: automaticBuildFileName)
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
                environmentId:                      synchConfig.tttEnvironmentId, 
                localConfig:                        true, 
                localConfigLocation:                tttConfigFolder, 
                folderPath:                         synchConfig.tttUtFolder, 
                recursive:                          true, 
                selectProgramsOption:               true, 
                jsonFile:                           changedProgramsFileName,
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
        }

        stage("SonarQube Scan") {

            def scannerHome           = tool synchConfig.sonarScanner

            // Find all of the Total Test results files that will be submitted to SonarQube
            // If result files have been created, build the Sonar parameters to pass test results
            def tttListOfResults      = findFiles(glob: 'TTTSonar/*.xml')   // Total Test SonarQube result files are stored in TTTSonar directory
            def sqTestResultsParm     = ''

            if(tttListOfResults != []) {
            sqTestResultsParm       = ' -Dsonar.tests="${synchConfig.tttUtFolder}" -Dsonar.testExecutionReportPaths='

            tttListOfResults.each {
                sqTestResultsParm     = sqTestResultsParm + 'TTTSonar/' + it.name +  ',' // Append the results file to the parm string
            }
            }
            
            // Check if Code Coverage data has been cerated
            // In that case, build the code coverage sonar parameter
            def listOfCoverageFiles   = findFiles(glob: 'Coverage/CodeCoverage.xml')
            def sqCoverageResultsParm = ''

            if(listOfCoverageFiles != []) {
            sqCoverageResultsParm = ' -Dsonar.coverageReportPaths=Coverage/CodeCoverage.xml'
            }

            withSonarQubeEnv(synchConfig.sonarServer) {

                bat '"' + scannerHome + '/bin/sonar-scanner"' + 
            //    " -Dsonar.branch.name=${BRANCH_NAME}" +
                sqTargetBranchParm +
                sqTestResultsParm +
                sqCoverageResultsParm +
                " -Dsonar.projectKey=RNU_${ispwConfig.ispwApplication.application}" + 
                " -Dsonar.projectName=RNU_${ispwConfig.ispwApplication.application}" + 
                " -Dsonar.projectVersion=1.0" +
                " -Dsonar.sources=${sonarCobolFolder}" + 
                " -Dsonar.cobol.copy.directories=${sonarCopybookFolder}" +
                " -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub" + 
                " -Dsonar.cobol.copy.suffixes=cpy" +
                " -Dsonar.sourceEncoding=UTF-8"

            }
        }   

    }
}