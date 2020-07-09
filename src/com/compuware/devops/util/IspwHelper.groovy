package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*
import com.compuware.devops.util.TaskInfo

/* Wrapper class to simplify use of ISPW functions */
class IspwHelper implements Serializable 
{
    def steps

    def String ispwUrl
    def String ispwRuntime
    def String ispwStream
    def String ispwApplication
    def String ispwRelease
    def String ispwContainer
    def String ispwContainerType    
    def String applicationPathNum
    def String ispwOwner
    def String ispwTargetLevel


    def String mfSourceFolder

    def String hciConnId
    def String hciTokenId

    IspwHelper(steps, pConfig) 
    {

        this.steps              = steps
        this.ispwUrl            = pConfig.ispwUrl
        this.ispwRuntime        = pConfig.ispwRuntime
        this.ispwStream         = pConfig.ispwStream
        this.ispwApplication    = pConfig.ispwApplication
        this.ispwRelease        = pConfig.ispwRelease        
        this.ispwContainer      = pConfig.ispwContainer
        this.ispwContainerType  = pConfig.ispwContainerType
        this.ispwOwner          = pConfig.ispwOwner
        this.ispwTargetLevel    = pConfig.ispwTargetLevel
        this.applicationPathNum = pConfig.applicationPathNum

        this.mfSourceFolder     = pConfig.mfSourceFolder

        this.hciConnId          = pConfig.hciConnId
        this.hciTokenId         = pConfig.hciTokenId
    }

    /* Download all sources from ISPW for a given level */
    def downloadAllSources(String ispwLevel)
    {

        steps.checkout( 
            changelog: false, 
            poll: false, 
            scm: [
                $class: 'IspwConfiguration', 
                    componentType: 'COB, COPY', 
                    connectionId: "${hciConnId}", 
                    credentialsId: "${hciTokenId}",      
                    folderName: '', 
                    ispwDownloadAll: true, 
                    levelOption: '0', 
                    serverApplication: "${ispwApplication}",
                    serverConfig: "${ispwRuntime}", 
                    serverLevel: "${ispwLevel}", 
                    serverStream: "${ispwStream}"
                ]
        )

    }

    /* Download sources for the ISPW Set which triggered the current pipeline from a given level */
    def downloadSources(String ispwLevel)
    {
        steps.checkout([
            $class:             'IspwContainerConfiguration', 
            componentType:      '',                                 // optional filter for component types in ISPW
            connectionId:       "${hciConnId}",     
            credentialsId:      "${hciTokenId}",      
            containerName:      "${ispwContainer}",   
            containerType:      "${ispwContainerType}",     // 0-Assignment 1-Release 2-Set
            ispwDownloadAll:    true,                              // false will not download files that exist in the workspace and haven't previous changed
            ispwDownloadIncl:   true,                               // Download "includes" not in the container
            serverConfig:       '',                                 // ISPW runtime config.  if blank ISPW will use the default runtime config
            serverLevel:        "${ispwLevel}"                                  // level to download the components from
        ])
    }

    /* Regress a list of assignments */
    def regressAssignmentList(assignmentList, cesToken)
    {
        for(int i = 0; i < assignmentList.size(); i++)
        {

            steps.echo "Regress Assignment ${assignmentList[0].toString()}, Level ${ispwTargetLevel}"

            regressAssignment(assignmentList[i], cesToken)

        }
            
    }

    /* Regress one assigment */
    def regressAssignment(assignment, cesToken)
    {
        steps.ispwOperation connectionId: hciConnId, 
            consoleLogResponseBody: true, 
            credentialsId: cesToken, 
            ispwAction: 'RegressAssignment', 
            ispwRequestBody: """runtimeConfiguration=${ispwRuntime}
                assignmentId=${assignment}
                level=${ispwTargetLevel}
                """
    }
}