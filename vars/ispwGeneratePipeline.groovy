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
def             generatePipelineResult

def call(Map pipelineParams)
{
    node
    {
        stage("Start of Unit Tests")
        {
            generatePipelineResult = Mainframe_Generate_Pipeline(pipelineParams)
        }
                
        stage("End of Unit Tests")
        {
            echo "Result: " + generatePipelineResult.pipelineResult
            echo "Text: " + generatePipelineResult.pipelineMailText
        }
        
        stage("Send Notification")
        {
            // Send Standard Email
            emailext subject:       '$DEFAULT_SUBJECT',
                        body:       '$DEFAULT_CONTENT \n' + mailMessageExtension,
                        replyTo:    '$DEFAULT_REPLYTO',
                        to:         "${pConfig.mailRecipient}"

        }        

    }   
}
