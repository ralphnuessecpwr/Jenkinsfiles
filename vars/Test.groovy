#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.lang.reflect.*
import java.net.URL
import com.compuware.devops.*

def call(Map pipelineParams)
{
    node
    {        
        def generatePipeline = load 'Mainframe_Generate_Pipeline.groovy'

        echo "Source Level: ${pipelineParams.ISPW_Src_Level}"
        pipelineParams.ISPW_Src_Level = pipelineParams.ISPW_Src_Level.replace('DEV', 'QA')
        echo "Source Level: ${pipelineParams.ISPW_Src_Level}"

        Mainframe_Generate_Pipeline.call(pipelineParams)

        stage("Promote")
        {
            echo "Starting Promote"
        }
    }
}