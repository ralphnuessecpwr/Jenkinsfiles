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
    // Build Code Coverage Test ID from current branch name and build number
    // The test id must not be londer than 15 characters
    //*********************************************************************************
    ccTestId = executionGitBranch.substring(executionGitBranch.length() - (CC_TEST_ID_MAX_LEN - BUILD_NUMBER.length() - 1)) + '_' + BUILD_NUMBER
}

def setVtLoadlibrary(){

    def jclSkeleton = readFile(tttUtJclSkeletonFile).toString().replace('${loadlibraries}', tttVtExecutionLoad)

    writeFile(
        file:   tttUtJclSkeletonFile,
        text:   jclSkeleton
    )    

}

def getSonarResults(){

    def resultsList         = ''
    def resultsFileContent  = readFile(file: sonarResultsFile)
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

            sleep 10

        }

        stage('Load code to mainframe') {

            sleep 10

        }

        stage('Build mainframe code') {

            sleep 10

        }

        stage('Execute Tests') {

            sleep 10

        }

        stage("SonarQube Scan") {

            sleep 10

        }   

    }
}