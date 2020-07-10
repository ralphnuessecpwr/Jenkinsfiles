#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
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

def initialize(pipelineParams)
{
    sonarQualityGateName = 'RNU_Gate'

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

    sonarHelper = new SonarHelper(this, steps, pConfig)

    sonarHelper.initialize()
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
        
        /* Download all sources that are part of the container */
        stage("Setup Sonar Project")
        {
            def sonarProjectName        = sonarHelper.determineUtProjectName()

            if(sonarHelper.checkForProject(sonarProjectName) == 'NOT FOUND')
            {
                echo "Project ${sonarProjectName} does not exist."

                sonarHelper.createProject(sonarProjectName)

                sonarHelper.setQualityGate(sonarQualityGateName, sonarProjectName)

                emailext subject:   "SonarQube Project created: ${sonarProjectName}",
                        body:       "Due to a checkout activity in application ${pConfig.ispw.application} SonarQube project" +
                                    " ${sonarProjectName} has been created and Quality Gate ${sonarQualityGateName} has been assigned to it.",
                        replyTo:    '$DEFAULT_REPLYTO',
                        to:         "${pConfig.mail.recipient}"
            }
            else
            {
                echo "Project ${sonarProjectName} already existed."
            }
        }
    }
}
