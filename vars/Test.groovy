#!/usr/bin/env groovy
/*
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
*/
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/*
Helper Classes for the Pipeline Script
*/
PipelineConfig  pConfig         // Pipeline configuration parameters
GitHelper       gitHelper       // Helper class for interacting with git and GitHub
IspwHelper      ispwHelper      // Helper class for interacting with ISPW
TttHelper       tttHelper       // Helper class for interacting with Topaz for Total Test
SonarHelper     sonarHelper     // Helper class for interacting with SonarQube

def             componentList           // List of components in the triggering set
def             componentStatusList     // List/Map of comonents and their corresponding componentStatus
                                        //  each entry will be of the for [componentName:componentStatus]
                                        //  with componentStatus being an instance of ComponentStatus
                                        //  to get to a status value use
                                        //  componentStatusList[componentName].value.<property>
                                        //  with <property> being one of the properties of a ComponentStatus

def             listOfExecutedTargets   // List of program names for which unit tests have been found and executed
String          cesToken                // Clear text token from CES
def             sourceResidenceLevel    // ISPW level at which the sources reside at the moment

private initialize(pipelineParams)
{
    /* Read list of mailaddresses from "private" Config File */
    /* The configFileProvider creates a temporary file on disk and returns its path as variable */
    def mailListlines

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

    // Instantiate and initialize Pipeline Configuration settings
    pConfig     = new   PipelineConfig(
                            steps, 
                            workspace,
                            pipelineParams,
                            mailListlines
                        )

    pConfig.initialize()                                            
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

    }
}