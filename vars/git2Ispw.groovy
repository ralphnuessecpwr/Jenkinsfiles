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
    
    branchMapping           = [:]

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

    synchConfig.branchInfo.each {
        echo it.toString()
        echo it.key
        echo it."${it.key}".ispwBranch.toString()
    }
/*
    if(executionGitBranch.contains('feature/FT1')){
        
    }
    else if(executionGitBranch.contains('feature/FT2')) {

    }
    else if(executionGitBranch.contains('feature/FT3')) {

    }
    else if(executionGitBranch.contains('bug')) {

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