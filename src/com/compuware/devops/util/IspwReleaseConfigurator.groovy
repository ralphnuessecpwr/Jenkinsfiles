package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*

/* Wrapper class to simplify use of ISPW functions */
class IspwReleaseConfigurator implements Serializable{

    def steps
    def pConfig
    def assignmentList

    IspwReleaseConfigurator(steps, pConfig, assignmentListString){

        this.steps              = steps
        this.pConfig            = pConfig
        this.assignmentList     = assignmentListString.split(',').collect{it.trim() as String}

    }

    private createRelease()
    {
        def mailMessagePart

        try
        {
            steps.ispwOperation(
                connectionId:           pConfig.hci.connectionId, 
                consoleLogResponseBody: true, 
                credentialsId:          pConfig.ces.jenkinsToken, 
                ispwAction:             'CreateRelease', 
                ispwRequestBody: """
                    stream=${pConfig.ispw.stream}
                    application=${pConfig.ispw.application}
                    releaseId=${pConfig.ispw.release}
                    description=Default Description
                    """
            )
            
            mailMessagePart = "Created release " + pConfig.ispw.release + ".\n"
        }
        catch (IllegalStateException e)
        {
            mailMessagePart = "Release " + pConfig.ispw.release + " already existed. Assignments were added.\n"
        }

        return mailMessagePart
    }    

    private addAssignments()
    {
        def mailMessagePart

        assignmentList.each
        {
            def currentAssignment   = it

            def response            = steps.httpRequest(
                url:                        "${pConfig.ispw.url}/ispw/${pConfig.ispw.runtime}/assignments/${it}/tasks",
                consoleLogResponseBody:     true, 
                customHeaders:              [[
                                            maskValue:  true, 
                                            name:       'authorization', 
                                            value:      pConfig.ces.token
                                            ]]
                
            )

            def taskList            = new JsonSlurper().parseText(response.getContent()).tasks

            def componentList       = []

            taskList.each
            {
                componentList.add(it.moduleName)        
            }

            taskList = null

            componentList.each
            {
                steps.echo "Task " + it
                
                steps.httpRequest(
                    httpMode:                   'POST',
                    url:                        "${pConfig.ispw.url}/ispw/${pConfig.ispw.runtime}/assignments/${currentAssignment}/tasks/transfer?mname=${it}",
                    consoleLogResponseBody:     true, 
                    contentType:                'APPLICATION_JSON', 
                    requestBody:                '''{
                                                    "runtimeConfiguration": "''' + pConfig.ispw.runtime + '''",
                                                    "containerId": "''' + pConfig.ispw.release + '''",
                                                    "containerType": "R"
                                                }''',
                    customHeaders:              [[
                                                maskValue:  true, 
                                                name:       'Authorization', 
                                                value:      pConfig.ces.token
                                                ]]
                )
            }

            mailMessagePart = mailMessagePart + "Added all tasks in assignment " + currentAssignment + " to Release " + pConfig.ispw.release + ".\n"

            return mailMessagePart
        }
    }

    private removeAssignments()
    {
        def mailMessagePart
        assignmentList.each
        {
            def currentAssignment   = it

            def response            = steps.httpRequest(
                url:                        "${pConfig.ispw.url}/ispw/${pConfig.ispw.runtime}/assignments/${it}/tasks",
                consoleLogResponseBody:     true, 
                customHeaders:              [[
                                            maskValue:  true, 
                                            name:       'authorization', 
                                            value:      pConfig.ces.token
                                            ]]
                
            )

            def taskList            = new JsonSlurper().parseText(response.getContent()).tasks

            def componentList       = []

            taskList.each
            {
                componentList.add(it.moduleName)        
            }

            taskList = null

            componentList.each
            {
                steps.echo "Task " + it
                
                steps.httpRequest(
                    httpMode:                   'POST',
                    url:                        "${pConfig.ispw.url}/ispw/${pConfig.ispw.runtime}/releases/${pConfig.ispw.release}/tasks/remove?mname=${it}",
                    consoleLogResponseBody:     true, 
                    contentType:                'APPLICATION_JSON', 
                    requestBody:                '''{
                                                    "runtimeConfiguration": "''' + pConfig.ispw.runtime + '''"
                                                }''',
                    customHeaders:              [[
                                                maskValue:  true, 
                                                name:       'Authorization', 
                                                value:      pConfig.ces.token
                                                ]]
                )
            }

            mailMessagePart = "Removed all tasks in assignment " + currentAssignment + " from Release " + pConfig.ispw.release + ".\n"
            return mailMessagePart
        }
    }    

    private checkReleaseReady()
    {
        def releaseReady        = true

        def failAssignmentList  = []

        def response        = steps.httpRequest(
            url:                        "${pConfig.ispw.url}/ispw/${pConfig.ispw.runtime}/releases/${pConfig.ispw.release}/tasks",
            consoleLogResponseBody:     true, 
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      pConfig.ces.token
                                        ]]
            
        )

        def taskList        = new JsonSlurper().parseText(response.getContent()).tasks

        taskList.each
        {

            if(
                it.level == 'DEV1' ||
                it.level == 'DEV2' ||
                it.level == 'DEV3'
            )
            {
                releaseReady     = false

                if(!failAssignmentList.contains(it.container))
                {
                    failAssignmentList.add(it.container)
                }
            }
            else
            {
                pConfig.ispw.targetLevel = it.level
            }
        }

        return failAssignmentList
    }
}