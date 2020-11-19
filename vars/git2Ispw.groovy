#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL
import groovy.xml.*

String executionBranch      
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
String sonarScanType    
String sonarCobolFolder        
String sonarCopybookFolder     
String sonarResultsFile   
String sonarResultsFileVt
String sonarResultsFileNvtBatch
String sonarResultsFileNvtCics
String sonarResultsFileList     
String sonarCodeCoverageFile   
String jUnitResultsFile

def branchMapping             
def ispwConfig
def synchConfig
def automaticBuildInfo
def executionMapRule
def programList
def tttProjectList

def BRANCH_TYPE_MAIN
def CC_TEST_ID_MAX_LEN
def CC_SYSTEM_ID_MAX_LEN
def SCAN_TYPE_NO_TESTS
def SCAN_TYPE_FULL

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

            setVtLoadlibrary()  /* Will be replaced by 20.05.01 features */

        }

        stage('Load code to mainframe') {

            try {

                gitToIspwIntegration( 
                    connectionId:       synchConfig.hciConnectionId,                    
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

                echo "Error during synchronisation to the mainframe.\n"
                echo e.toString()
                currentBuild.result = 'FAILURE'
                return

            }

        }

        // If the automaticBuildParams.txt has not been created, it means no programs
        // have been changed and the pipeline was triggered for other changes (e.g. in configuration files)
        // These changes do not need to be "built".
        try {
            automaticBuildInfo = readJSON(file: automaticBuildFile)
        }
        catch(Exception e) {

            echo "No Automatic Build Params file was found.  Meaning, no mainframe sources have been changed.\n" +
            "Mainframe Build and Test steps will be skipped. Sonar scan will be executed against code only."

            sonarScanType = SCAN_TYPE_NO_TESTS

        }

        stage('Build mainframe code') {

            if(sonarScanType == SCAN_TYPE_FULL){

                ispwOperation(
                    connectionId:           synchConfig.hciConnectionId, 
                    credentialsId:          pipelineParms.cesCredentialsId,       
                    consoleLogResponseBody: true, 
                    ispwAction:             'BuildTask', 
                    ispwRequestBody:        '''buildautomatically = true'''
                )

            }
            else{

                echo "Skipping Mainframe Build."
            
            }
        }

        stage('Execute Unit Tests') {
            
            if(sonarScanType == SCAN_TYPE_FULL){

                totaltest(
                    serverUrl:                          synchConfig.cesUrl, 
                    credentialsId:                      pipelineParms.hostCredentialsId, 
                    environmentId:                      synchConfig.tttVtEnvironmentId,
                    localConfig:                        true, 
                    localConfigLocation:                tttConfigFolder, 
                    folderPath:                         tttRootFolder, 
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
                //    ccThreshold:                        synchConfig.ccThreshold,     
                    logLevel:                           'INFO'
                )

                /* Prevent replacing of VT results file if VT and NVT is executed */
                bat label:  'Rename', 
                    script: """
                        cd ${sonarResultsFolder}
                        ren ${sonarResultsFile} ${sonarResultsFileVt}
                    """

                sonarResultsFileList.add(sonarResultsFileVt)

            }
            else{

                echo "Skipping Unit Tests."

            }
        }

        if(pipelineParms.branchType == 'main') {

            stage('Execute Module Integration Tests') {

                if(sonarScanType == SCAN_TYPE_FULL){

                    /* Execute batch scenarios */
                    totaltest(
                        serverUrl:                          synchConfig.cesUrl, 
                        credentialsId:                      pipelineParms.hostCredentialsId, 
                        environmentId:                      synchConfig.tttNvtBatchEnvironmentId, 
                        localConfig:                        false,
                        localConfigLocation:                tttConfigFolder, 
                        folderPath:                         tttRootFolder, 
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
                    //    ccThreshold:                        pipelineParms.ccThreshold,     
                        logLevel:                           'INFO'
                    )

                    bat label:  'Rename', 
                        script: """
                            cd ${sonarResultsFolder}
                            ren ${sonarResultsFile} ${sonarResultsFileNvtBatch}
                        """

                    sonarResultsFileList.add(sonarResultsFileNvtBatch)

                    /* Execute CICS scenarios */
                    totaltest(
                        serverUrl:                          synchConfig.cesUrl, 
                        credentialsId:                      pipelineParms.hostCredentialsId, 
                        environmentId:                      synchConfig.tttNvtCicsEnvironmentId, 
                        localConfig:                        false,
                        localConfigLocation:                tttConfigFolder, 
                        folderPath:                         tttRootFolder, 
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
                    //    ccThreshold:                        pipelineParms.ccThreshold,     
                        logLevel:                           'INFO'
                    )

                    // If the automaticBuildParams.txt has not been created, it means no programs
                    // have been changed and the pipeline was triggered for other changes (e.g. in configuration files)
                    // These changes do not need to be "built".
                    try {

                        bat label:  'Rename', 
                            script: """
                                cd ${sonarResultsFolder}
                                ren ${sonarResultsFile} ${sonarResultsFileNvtCics}
                            """

                        sonarResultsFileList.add(sonarResultsFileNvtCics)
                        
                    }
                    catch(Exception e) {

                        echo "No CICS results file."

                    }

                }
                else{

                    echo "Skipping Integration Tests."

                }
            }
        }

        if(sonarScanType == SCAN_TYPE_FULL){

            step([
                $class:             'CodeCoverageBuilder', 
                connectionId:       synchConfig.hciConnectionId, 
                credentialsId:      pipelineParms.hostCredentialsId,
                analysisProperties: """
                    cc.sources=${ccSources}
                    cc.repos=${pipelineParms.ccRepo}
                    cc.system=${ccSystemId}
                    cc.test=${ccTestId}
                    cc.ddio.overrides=${ccDdioOverrides}
                """
            ])

        }
            
        stage("SonarQube Scan") {

            def sonarBranchParm         = ''
            def sonarTestResults        = ''
            def sonarTestsParm          = ''
            def sonarTestReportsParm    = ''
            def sonarCodeCoverageParm   = ''
            def scannerHome             = tool synchConfig.sonarScanner            

            if(sonarScanType == SCAN_TYPE_FULL){

                sonarTestResults        = getSonarResults(sonarResultsFileList)
                sonarTestsParm          = ' -Dsonar.tests="' + tttRootFolder + '"'
                sonarTestReportsParm    = ' -Dsonar.testExecutionReportPaths="' + sonarTestResults + '"'
                sonarCodeCoverageParm   = ' -Dsonar.coverageReportPaths=' + sonarCodeCoverageFile

            }

            withSonarQubeEnv(synchConfig.sonarServer) {

                bat '"' + scannerHome + '/bin/sonar-scanner"' + 
                ' -Dsonar.branch.name=' + executionBranch +
                ' -Dsonar.projectKey=' + ispwConfig.ispwApplication.stream + '_' + ispwConfig.ispwApplication.application + 
                ' -Dsonar.projectName=' + ispwConfig.ispwApplication.stream + '_' + ispwConfig.ispwApplication.application +
                ' -Dsonar.projectVersion=1.0' +
                ' -Dsonar.sources=' + sonarCobolFolder + 
                ' -Dsonar.cobol.copy.directories=' + sonarCopybookFolder +
                ' -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,result' + 
                ' -Dsonar.cobol.copy.suffixes=cpy' +
                sonarTestsParm +
                sonarTestReportsParm +
                sonarCodeCoverageParm +
                ' -Dsonar.ws.timeout=480' +
                ' -Dsonar.sourceEncoding=UTF-8'

            }
        }   
    }
}

def initialize(){

    CC_TEST_ID_MAX_LEN          = 15
    CC_SYSTEM_ID_MAX_LEN        = 15

    SCAN_TYPE_NO_TESTS          = "NoTests"
    SCAN_TYPE_FULL              = "Full"

    BRANCH_TYPE_MAIN            = 'main'

    executionBranch             = BRANCH_NAME
    sharedLibName               = 'RNU_Shared_Lib'                  /* Rename in Jenkins server */
    synchConfigFile             = './git2ispw/synchronization.yml'
    automaticBuildFile          = './automaticBuildParams.txt'
    changedProgramsFile         = './changedPrograms.json'
    branchMappingString         = ''    
    tttConfigFolder             = ''
    tttVtExecutionLoad          = ''
    ccDdioOverrides             = ''
    sonarScanType               = SCAN_TYPE_FULL
    sonarResultsFile            = 'generated.cli.suite.sonar.xml'
    sonarResultsFileVt          = 'generated.cli.vt.suite.sonar.xml'
    sonarResultsFileNvtBatch    = 'generated.cli.nvt.batch.suite.sonar.xml'
    sonarResultsFileNvtCics     = 'generated.cli.nvt.cics.suite.sonar.xml'
    sonarResultsFileList        = []    
    sonarResultsFolder          = './TTTSonar'
    sonarCodeCoverageFile       = './Coverage/CodeCoverage.xml'
    jUnitResultsFile            = './TTTUnit/generated.cli.suite.junit.xml'

    //*********************************************************************************
    // Read synchconfig.yml from Shared Library resources folder
    //*********************************************************************************
    def fileText    = libraryResource synchConfigFile
    
    synchConfig     = readYaml(text: fileText)

    //*********************************************************************************
    // Build paths to subfolders of the project root
    //*********************************************************************************

    ispwConfigFile          = synchConfig.mfProjectRootFolder + '/ispwconfig.yml'
    tttRootFolder           = synchConfig.mfProjectRootFolder + '/Tests'
    ccSources               = synchConfig.mfProjectRootFolder + '/Sources'
    sonarCobolFolder        = synchConfig.mfProjectRootFolder + '/Sources'
    sonarCopybookFolder     = synchConfig.mfProjectRootFolder + '/Sources'

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
    // Build branch mapping string to be used as parameter in the gitToIspwIntegration
    // Build load library name from configuration, replacing application marker by actual name
    //*********************************************************************************
    synchConfig.branchInfo.each {

        branchMappingString = branchMappingString + it.key + '** => ' + it.value.ispwLevel + ',' + it.value.mapRule + '\n'

        if(executionBranch.contains(it.key)) {
            tttVtExecutionLoad = synchConfig.loadLibraryPattern.replace('<ispwApplication>', ispwConfig.ispwApplication.application).replace('<ispwLevel>', it.value.ispwLevel)
        }
    }

    //*********************************************************************************
    // If load library name is empty the branch name could not be mapped
    //*********************************************************************************
    if(tttVtExecutionLoad == ''){
        error "No branch mapping for branch ${executionBranch} was found. Execution will be aborted.\n" +
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
    if(executionBranch.length() > CC_SYSTEM_ID_MAX_LEN) {
        ccSystemId  = executionBranch.substring(executionBranch.length() - CC_SYSTEM_ID_MAX_LEN)
    }
    else {
        ccSystemId  = executionBranch
    }
    
    ccTestId    = BUILD_NUMBER
}

/* Modify JCL Skeleton to use correct load library for VTs */
/* Will be replaced by 20.05.01 feature                    */
def setVtLoadlibrary(){

    def jclSkeleton = readFile(tttUtJclSkeletonFile).toString().replace('${loadlibraries}', tttVtExecutionLoad)

    writeFile(
        file:   tttUtJclSkeletonFile,
        text:   jclSkeleton
    )    

}

def getSonarResults(resultsFileList){

    def resultsList         = ''

    resultsFileList.each{

        def resultsFileContent
        resultsFileContent  = readFile(file: sonarResultsFolder + '/' + it)
        resultsFileContent  = resultsFileContent.substring(resultsFileContent.indexOf('\n') + 1)
        def testExecutions  = new XmlSlurper().parseText(resultsFileContent)

        testExecutions.file.each {

            resultsList = resultsList + it.@path.toString().replace('.result', '.sonar.xml') + ','

        }
    }

    return resultsList
}