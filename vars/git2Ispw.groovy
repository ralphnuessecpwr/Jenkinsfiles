#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import java.net.URL

// read config files
// determine application name
// determine branch 
// build load library
// build DDIO

def initialize(){

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

    echo "Test"
    echo synchConfig.branchInfo.feature/FT1.ispwBranch
/*
    if(executionGitBranch.contains('feature/FT1')){

    }
    else if(executionGitBranch.contains('feature/FT2')) {

    }
    else if(executionGitBranch.contains('feature/FT3')) {

    }
    else if(executionGitBranch.contains('Bug/')) {

    }
    else if(executionGitBranch.contains('master')) {

    }
    else {
        error "No branch mapping for branch ${executionGitBranch} was found. Execution will be aborted.\n" +
            "Correct the branch name to reflect naming conventions."
    }

*/
}

def call(Map pipelineParms){

    String ispwConfigFileName      = 'ispwconfig.yml'
    String synchConfigFolder       = 'git2ispw'
    String synchConfigFileName     = 'synchronizationconfig.yml'
    String automaticBuildFileName  = 'automaticBuildParams.txt'
    String testAssetsPath          = 'executedTests'
    String ccDdioOverrides         = ''
    String executionGitBranch      = BRANCH_NAME
    String branchMappingString     = ''
    
    def branchMapping              = [:]

    def ispwConfig
    def synchConfig
    def automaticBuildInfo
    def executionMapRule
    def programList
    def tttProjectList

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
    }
}