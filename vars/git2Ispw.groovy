#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL

// determine application name
// determine branch 
// build load library
// build DDIO

String ispwConfigFileName     
String synchConfigFolder      
String synchConfigFileName    
String automaticBuildFileName 
String testAssetsPath         
String ccDdioOverrides        
String executionGitBranch     
String branchMappingString    

def branchMapping             

def ispwConfig
def synchConfig
def automaticBuildInfo
def executionMapRule
def programList
def tttProjectList


def initialize(){

    ispwConfigFileName      = 'ispwconfig.yml'
    synchConfigFolder       = 'git2ispw'
    synchConfigFileName     = 'synchronizationconfig.yml'
    automaticBuildFileName  = 'automaticBuildParams.txt'
    testAssetsPath          = 'executedTests'
    ccDdioOverrides         = ''
    executionGitBranch      = 'feature/FT2-new-feature'
    branchMappingString     = ''
    tttVtExecutionLoad      = ''
    
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
    def synchConfig = readYaml(text: fileText)

    echo synchConfig.branchInfo.toString()
    echo synchConfig.branchInfo."feature/FT1".ispwBranch.toString()

    synchConfig.branchInfo.each {

        branchMappingString = branchMappingString + it.key + '** => ' + it.value.ispwBranch + ',' + it.value.mapRule + '\n'

        if(executionGitBranch.contains(it.key)) {
            tttVtExecutionLoad = it.value.loadLib.replace('<ispwApplication>', ispwConfig.ispwApplication.application)
        }
    }

    echo "Mapping"
    echo branchMappingString
    echo "Load"
    echo tttVtExecutionLoad

    if(tttVtExecutionLoad == ''){
        error "No branch mapping for branch ${executionGitBranch} was found. Execution will be aborted.\n" +
            "Correct the branch name to reflect naming conventions."
    }

    synchConfig.ccDdioOverrides.each {
        ccDdioOverrides = ccDdioOverrides + it.toString().replace('<ispwApplication>', ispwConfig.ispwApplication.application)
    }

    echo "DDIO"
    echo ccDdioOverrides
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
                    app: ispwConfig.ispwApplication.application, 
                    connectionId: pipelineParams.hciConnectionId,
                    credentialsId: pipelienParams.hostCredentialsId, 
                    gitCredentialsId: pipelineParams.gitCredentialsId, 
                    gitRepoUrl: pipelineParams.gitRepoUrl, 
                    ispwConfigPath: ispwConfigFileName, 
                    runtimeConfig: ispwConfig.ispwApplication.runtimeConfig,
                    stream: ispwConfig.ispwApplication.stream
                )

            }
            catch(Exception e) {

                echo "No Synchronisation to the mainframe.\n"
                currentBuild.result = 'SUCCESS'
                return

            }
            
        }
    }
}