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
def             unitTestStepsResult
def             generatePipelineResult
def             programStatusList

def call(Map pipelineParams)
{
    node
    {
        stage("Start of Unit Tests")
        {
            generatePipelineResult = mainframeUnitTestSteps(pipelineParams)
        }
                
        stage("End of Unit Tests")
        {
            unitTestStepsResult     = generatePipelineResult.pipelineResult
            mailMessageExtension    = generatePipelineResult.pipelineMailText
            pConfig                 = generatePipelineResult.pipelineConfig
            programStatusList       = generatePipelineResult.pipelineProgramStatusList
            
            echo "Unit Test Steps finished \n" +
                "Result : ${generatePipelineResult.pipelineResult} \n" +
                "Program Status: " + programStatusList
            
            for (programStatus in programStatusList)
            {
                echo "Program ${programStatus.key}, status ${programStatus.value}"
            }
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
