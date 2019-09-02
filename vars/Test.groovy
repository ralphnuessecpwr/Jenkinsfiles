#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
import com.compuware.devops.*

def call(Map pipelineParams)
{
    node
    {        
        pipelineParams.ISPW_Src_Level.replace('DEV', 'QA')
        
        Mainframe_Generate_Pipeline.call()

        stage("Promote")
        {
            echo "Starting Promote"
        }
    }
}