#!/usr/bin/env groovy
import hudson.model.*
import hudson.EnvVars
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import jenkins.plugins.http_request.*
import java.net.URL
import com.compuware.devops.*

JclSkeleton     jclSkeleton
String          ispwApplication
String          ispwPathNum

def initialize(pipelineParams)
{
    // Clean out any previously downloaded source
    dir(".\\") 
    {
        deleteDir()
    }

    ispwApplication = 'RXN3'
    ispwPathNum     = '1'

    jclSkeleton = new JclSkeleton(steps, workspace, ispwApplication, ispwPathNum)

    jclSkeleton.initialize()
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
                
        stage("Test")
        {
            String jcl = jclSkeleton.createCleanUpCcRepo(ispwApplication, BUILD_NUMBER)

            echo "Built JCL\n\n" + jcl

            jcl = jclSkeleton.createIebcopyCopyBooksJcl('TEST.DS.NAME', ['A','B','C'])

            echo "Built JCL\n\n" + jcl

            jcl = jclSkeleton.createDeleteTempDsn('TEST.DS.NAME')

            echo "Built JCL\n\n" + jcl
        }
    }
}
