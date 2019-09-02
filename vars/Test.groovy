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

    Class c = Mainframe_Generate_Pipeline.getClass();
    for (Method method : c.getDeclaredMethods()) 
    {
        echo(method.getName());
    }
}

    node
    {        
        echo "Source Level: ${pipelineParams.ISPW_Src_Level}"
        pipelineParams.ISPW_Src_Level.replace('DEV', 'QA')
        echo "Source Level: ${pipelineParams.ISPW_Src_Level}"

        Mainframe_Generate_Pipeline.call(pipelineParams)

        stage("Promote")
        {
            echo "Starting Promote"
        }
    }
}