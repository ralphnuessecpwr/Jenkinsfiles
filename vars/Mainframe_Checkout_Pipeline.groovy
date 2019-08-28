#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/
PipelineConfig  pConfig
GitHelper       gitHelper
IspwHelper      ispwHelper
TttHelper       tttHelper
SonarHelper     sonarHelper 

String          mailMessageExtension
String          sonarQualityGateId
String          cesToken

def             componentList
def             sonarProjectList
def             messageText
def             sonarProjectName

def initialize(pipelineParams)
{
    // Clean out any previously downloaded source
    dir(".\\") 
    {
        deleteDir()
    }

    def mailListlines
    /* Read list of mailaddresses from "private" Config File */
    /* The configFileProvider creates a temporary file on disk and returns its path as variable */
    configFileProvider(
        [
            configFile(
                fileId: 'MailList', 
                variable: 'mailListFilePath'
            )
        ]
    ) 
    {
        File mailConfigFile = new File(mailListFilePath)

        if(!mailConfigFile.exists())
        {
            steps.error "File - ${mailListFilePath} - not found! \n Aborting Pipeline"
        }

        mailListlines = mailConfigFile.readLines()
    }

    pConfig     = new   PipelineConfig(
                            steps, 
                            workspace,
                            pipelineParams,
                            mailListlines
                        )

    pConfig.initialize()                                            

    ispwHelper  = new   IspwHelper(
                            steps, 
                            pConfig
                        )

    sonarHelper = new SonarHelper(this, steps, pConfig)

    sonarHelper.initialize()

    withCredentials(
        [string(credentialsId: "${pConfig.cesTokenId}", variable: 'cesTokenTemp')]
    ) 
    {
        cesToken = cesTokenTemp
    }

    componentList       = []
    sonarProjectList    = []
    messageText         = ''
}

def setupSonarProject(String sonarProjectName, String sonarProjectGate)
{   
    sonarHelper.createProject(sonarProjectName)
    sonarHelper.setQualityGate(sonarProjectGate, sonarProjectName)
    sonarHelper.initialScan(sonarProjectName)
    sonarProjectList.add(sonarProjectName)
}

/**
Call method to execute the pipeline from a shared library
@param pipelineParams - Map of paramter/value pairs
*/
def call(Map pipelineParams)
{
    node
    {
        stage("Initialization")
        {
            initialize(pipelineParams)
        }

        stage("Download Assignment Sources")
        {
            

            ispwHelper.downloadSourcesForAssignment(pConfig.ispwSrcLevel)
            ispwHelper.downloadCopyBooks(workspace)
        }

        /* Download all sources that are part of the container */
        stage("Setup Sonar Project for Unit Tests")
        {
            sonarProjectName        = sonarHelper.determineProjectName('UT')

            if(sonarHelper.checkForProject(sonarProjectName) == 'NOT FOUND')
            {
                setupSonarProject(sonarProjectName, 'RNU_Gate')
            }
        }

        stage("Setup Sonar Project for Functional Tests")
        {
            sonarProjectName        = sonarHelper.determineProjectName('FT')

            if(sonarHelper.checkForProject(sonarProjectName) == 'NOT FOUND')
            {
                setupSonarProject(sonarProjectName, 'RNU_Gate_FT')
            }
        }

        stage("Setup Sonar Application Project")
        {
            sonarProjectName        = sonarHelper.determineProjectName('Application')

            if(sonarHelper.checkForProject(sonarProjectName) == 'NOT FOUND')
            {
                ispwHelper.downloadAllSources(pConfig.ispwSrcLevel)
                setupSonarProject(sonarProjectName, 'RNU_Gate')
            }
        }

        stage("Send notification")
        {
            messageText     = "Executed checkout in application ${pConfig.ispwApplication}, assignment ${pConfig.ispwAssignment}.\n"
            componentList   = ispwHelper.getComponents(cesToken, pConfig.ispwContainer, pConfig.ispwContainerType)

            def componentListMessage = ''

            if(componentList.size() == 0)
            {
                componentListMessage = '\nNo COBOL Components were checked out.\n'
            }
            else
            {
                componentListMessage = '\nThe Following COBOL components were checked out:\n'
                componentList.each
                {
                    componentListMessage = componentListMessage + it + '\n'
                }
            }

            componentListMessage = componentListMessage + '\n'

            def sonarProjectListMessage = ''

            if(sonarProjectList.size() == 0)
            {
                sonarProjectListMessage = 'No new SonarQube projects were defined.'
            }
            else
            {
                sonarProjectListMessage = 'The following SonarQube projects were defined:\n'
                sonarProjectList.each
                {
                    sonarProjectListMessage = sonarProjectListMessage + it + '\n'
                }
            }

            messageText = messageText + componentListMessage + sonarProjectListMessage

            emailext subject:   "Checkout for Assigmnment ${pConfig.ispwAssignment}",
                body:       messageText,
                replyTo:    '$DEFAULT_REPLYTO',
                to:         pConfig.mailRecipient
        }
    }
}
