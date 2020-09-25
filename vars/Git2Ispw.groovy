#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
import com.compuware.devops.util.*

def call(Map pipelineParams){

    def ispwConfigFileName      = 'ispwconfig.yml'
    def synchConfigFileName     = 'synchronizationconfig.yml'
    def automaticBuildFileName  = 'automaticBuildParams.txt'
    def testAssetsPath          = 'executedTests'
    def ccDdioOverrides         = ''

    def ispwConfig
    def synchConfig
    def automaticBuildInfo

    def executionGitBranch      = BRANCH_NAME
    def executionMapRule
    def branchMapping           = ''

    def programList
    def tttProjectList

    //************************************************************
    // Method to determine the components from the assignment
    //************************************************************
    def buildProgramList(automaticBuildInfo, synchConfig) {

    def resp = ispwOperation connectionId: synchConfig.hciConnectionId, 
        credentialsId: synchConfig.cesCredentialsId,
        consoleLogResponseBody: true, 
        ispwAction: 'GetAssignmentTaskList', 
        ispwRequestBody: """assignmentId=${automaticBuildInfo.containerId}
        level=${automaticBuildInfo.taskLevel}
        """ 

    def programList = []

    def response = readYaml(text: resp.getContent().toString())

    response.tasks.each
    {
        if(automaticBuildInfo.taskIds.contains(it.taskId)) {
        programList.add(it.moduleName)
        }
    }

    return programList
    }

    //************************************************************
    // Method to build the list of TTT projects to execute from the list of programs 
    //************************************************************
    def buildProjectList(programList) {
    def tttProjectList = []

    programList.each {
        def programName = it
        // Search for any .testscenario file that contains the component name as part of its name
        listOfScenarios = findFiles(glob: '**/'+ it + '*.testscenario')

        listOfScenarios.each {
        def scenarioPath        = it.path
        def projectName         = scenarioPath.toString().substring(0, scenarioPath.toString().indexOf('\\Unit Test'))
        def scenarioFullName    = it.name

        // Add project name to project list, if not present already
        if(!tttProjectList.contains(projectName)) {
            tttProjectList.add(projectName)
        }
        }
    }

    return tttProjectList
    }

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

            //*********************************************************************************
            // Strip the first line of ispwconfig.yml because readYaml can't handle the !! tag
            //*********************************************************************************
            def tmpText = readFile(file: ispwConfigFileName)

            // remove the first line (i.e. the substring following the first carriage return '\n')
            tmpText = tmpText.substring(tmpText.indexOf('\n') + 1)

            // convert the text to yaml
            ispwConfig = readYaml(text: tmpText)

            synchConfig = readYaml(file: synchConfigFileName)

            // Determine ISPW branch and build branch mapping
            synchConfig.branchMapping.each {

                branchMapping       = branchMapping + it.gitBranch + ' => ' + it.ispwBranch.toString().replace('<ispwPath>', synchConfig.ispwPath) + ', ' + it.mapRule + '\n'

            }

            if(branchMapping == '') {
            error "No branch mapping for branch ${executionGitBranch} was found. Execution will be aborted.\n" +
                "Correct the synchronizationconfig.yml file in your project's root folder."
            }

            // Build DDIO Overrides String for Code Coverage results
            // Replace placeholder for ISPW application name
            synchConfig.ccDdioOverrides.each {
            ccDdioOverrides = ccDdioOverrides + it.toString().replace('<ispwApplication>', ispwConfig.ispwApplication.application)
            }
        }

        stage('Load code to mainframe') {

            try {

            gitToIspwIntegration app:   ispwConfig.ispwApplication.application, 
                branchMapping:          branchMapping,
                connectionId:           synchConfig.hciConnectionId, 
                credentialsId:          synchConfig.hostCredentialsId, 
                gitCredentialsId:       synchConfig.gitCredentialsId, 
                gitRepoUrl:             synchConfig.gitRepoUrl, 
                runtimeConfig:          ispwConfig.ispwApplication.runtimeConfig, 
                stream:                 ispwConfig.ispwApplication.stream

            }
            catch(Exception e) {

            echo "No Synchronisation to the mainframe.\n"
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

            ispwOperation connectionId:   synchConfig.hciConnectionId, 
                consoleLogResponseBody:   true, 
                credentialsId:            synchConfig.cesCredentialsId,       
                ispwAction:               'BuildTask', 
                ispwRequestBody:          '''buildautomatically = true'''

        }

        programList     = buildProgramList(automaticBuildInfo, synchConfig)

        tttProjectList  = buildProjectList(programList)

        stage("Execute Tests") {

            // Initially clear cc statistics
            def ccClear = true

            tttProjectList.each {
            echo "*************************\n"        +
                "Execute scenarios in project ${it}\n"    +
                "*************************"

            step(
                [
                $class: 'TotalTestBuilder', 
                    connectionId:   synchConfig.hciConnectionId,    
                    credentialsId:  synchConfig.hostCredentialsId, 
                    projectFolder:  it,
                    testSuite:      'All_Scenarios',
                    jcl:            '"Unit Test/JCL/RUNNER_' + automaticBuildInfo.taskLevel + '.jcl"',
                    useStubs:       true,
                    deleteTemp:     true,                           
                    hlq:            '',                             
                    ccClearStats:   ccClear,                          
                    ccRepo:         synchConfig.ccRepo,
                    ccSystem:       ispwConfig.ispwApplication.application, 
                    ccTestId:       BUILD_NUMBER
                ]
            )

            // Don't clear cc statistics for next executions
            ccClear = false
            }

            step(
            [
                $class:             'CodeCoverageBuilder', 
                connectionId:       synchConfig.hciConnectionId, 
                credentialsId:      synchConfig.hostCredentialsId,
                analysisProperties: """
                cc.sources=Cob
                cc.repos=${synchConfig.ccRepo}
                cc.system=${ispwConfig.ispwApplication.application}
                cc.test=${BUILD_NUMBER}
                cc.ddio.overrides=${ccDdioOverrides}
                """
            ]
            )
        }

        stage("SonarQube Scan") {
            def scannerHome           = tool synchConfig.sonarScanner

            // Find all of the Total Test results files that will be submitted to SonarQube
            // If result files have been created, build the Sonar parameters to pass test results
            def tttListOfResults      = findFiles(glob: 'TTTSonar/*.xml')   // Total Test SonarQube result files are stored in TTTSonar directory
            def sqTestResultsParm     = ''

            if(tttListOfResults != []) {
            sqTestResultsParm       = ' -Dsonar.tests="Unit Test" -Dsonar.testExecutionReportPaths='

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

            // For the master branch no target branch parm must be created
            def sqTargetBranchParm

            if(BRANCH_NAME == 'master') {
            sqTargetBranchParm = ''
            }
            else {
            sqTargetBranchParm = ' -Dsonar.branch.target=master'
            }

            withSonarQubeEnv(synchConfig.sonarServer) {

                bat '"' + scannerHome + '/bin/sonar-scanner"' + 
                " -Dsonar.branch.name=${BRANCH_NAME}" +
                sqTargetBranchParm +
                sqTestResultsParm +
                sqCoverageResultsParm +
                " -Dsonar.projectKey=RNU_${ispwConfig.ispwApplication.application}" + 
                " -Dsonar.projectName=RNU_${ispwConfig.ispwApplication.application}" + 
                " -Dsonar.projectVersion=1.0" +
                " -Dsonar.sources=Cob" + 
                " -Dsonar.cobol.copy.directories=CobCopy" +
                " -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub" + 
                " -Dsonar.cobol.copy.suffixes=cpy" +
                " -Dsonar.sourceEncoding=UTF-8"

            }
        }   
    }    

}