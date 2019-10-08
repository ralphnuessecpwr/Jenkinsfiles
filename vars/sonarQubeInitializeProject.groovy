#!/usr/bin/env groovy
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/
PipelineConfig  pConfig         // Pipeline configuration parameters
IspwHelper      ispwHelper      // Helper class for interacting with ISPW
SonarHelper     sonarHelper     // Helper class for interacting with SonarQube

String          cesToken                // Clear text token from CES
def             sourceResidenceLevel    // ISPW level at which the sources reside at the moment

private initialize(pipelineParams)
{
    // Clean out any previously downloaded source
    dir(".\\") 
    {
        deleteDir()
    }

    // Instantiate and initialize Pipeline Configuration settings
    pConfig     = new   PipelineConfig(
                            steps, 
                            workspace,
                            pipelineParams,
                            mailListlines
                        )

    pConfig.initialize()                                            

    // Use Jenkins Credentials Provider plugin to retrieve CES token in clear text from the Jenkins token for the CES token
    // The clear text token is needed for native http REST requests against the ISPW API
    withCredentials(
        [string(credentialsId: "${pConfig.cesTokenId}", variable: 'cesTokenTemp')]
    ) 
    {
        cesToken = cesTokenTemp
    }

    // Instanatiate and initialize the ISPW Helper
    ispwHelper  = new   IspwHelper(
                            steps, 
                            pConfig
                        )

    // Instantiate and initialize the Sonar Helper
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
                
        /* Download all sources that are part of the container  */
        stage("Retrieve Mainframe Code")
        {
            ispwHelper.downloadAllSources('PRD')
        }

        // Scan sources and fail fast
        stage("Initial Scan and set Quality Gate")
        {            
            def sonarProjectName    = pConfig.ispwStream + '_' + pConfig.ispwApplication
            def sqQualityGateId     = sonarHelper.getQualityGateId(pConfig.sqQualityGateName)

            if(sonarHelper.checkForProject(sonarProjectName) == 'NOT FOUND')
            {
                sonarHelper.createProject(sonarProjectName)
                
                sonarHelper.setQualityGate(sonarProjectGate, sonarProjectName)

                sonarHelper.scanSources(sonarProjectName)
            }            
        }
    }
}