package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*
import com.compuware.devops.util.TaskInfo

class IspwHelper implements Serializable 
{

    def steps
    def String ispwUrl
    def String ispwRuntime
    def String ispwRelease
    def String ispwContainer
    def String ispwContainerType    

    def String hciConnId
    def String hciTokenId

    //String ispwUrl, String ispwRuntime, String ispwRelease, String ispwContainer)

    IspwHelper(steps, config) 
    {

        this.steps              = steps
        this.ispwUrl            = config.ispwUrl
        this.ispwRuntime        = config.ispwRuntime
        this.ispwRelease        = config.ispwRelease        
        this.ispwContainer      = config.ispwContainer
        this.ispwContainerType  = config.ispwContainerType

        this.hciConnId          = config.hciConnId
        this.hciTokenId         = config.hciTokenId

    }

    def downloadSources()
    {
        steps.checkout([
            $class:             'IspwContainerConfiguration', 
            componentType:      '',                                 // optional filter for component types in ISPW
            connectionId:       "${pConfig.hciConnId}",     
            credentialsId:      "${pConfig.hciTokenId}",      
            containerName:      "${pConfig.ispwContainer}",   
            containerType:      "${pConfig.ispwContainerType}",     // 0-Assignment 1-Release 2-Set
            ispwDownloadAll:    false,                              // false will not download files that exist in the workspace and haven't previous changed
            serverConfig:       '',                                 // ISPW runtime config.  if blank ISPW will use the default runtime config
            serverLevel:        ''                                  // level to download the components from
        ])                           
    }

/* 
    Receive a list of task IDs and the response of an "List tasks of a Release"-httpRequest to build and return a list of Assignments
    that are contained in both the Task Id List and the List of Tasks in the Release 
*/
    def ArrayList getAssigmentList(String cesToken, String level)
    {
        def returnList  = []

        def taskIds     = getSetTaskIdList(cesToken, level)

        def response = steps.httpRequest(
            url:                        "${ispwUrl}/ispw/${ispwRuntime}/releases/${ispwRelease}/tasks",
            consoleLogResponseBody:     false, 
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            )

        def jsonSlurper = new JsonSlurper()
        def resp        = jsonSlurper.parseText(response.getContent())
        response        = null
        jsonSlurper     = null

        if(resp.message != null)
        {
            steps.echo "Resp: " + resp.message
            error
        }
        else
        {
            def taskList = resp.tasks

            taskList.each
            {
                if(taskIds.contains(it.taskId))
                {
                    if(!(returnList.contains(it.container)))
                    {
                        returnList.add(it.container)        
                    }
                }
            }
        }

        return returnList    

    }


/* 
    Receive a response from an "Get Tasks in Set"-httpRequest and build and return a list of task IDs that belong to the desired level
*/
    def ArrayList getSetTaskIdList(String cesToken, String level)
    {
        def returnList  = []

        def response = steps.httpRequest(
            url:                        "${ispwUrl}/ispw/${ispwRuntime}/sets/${ispwContainer}/tasks",
            httpMode:                   'GET',
            consoleLogResponseBody:     false,
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            )

        def jsonSlurper = new JsonSlurper()
        def resp        = jsonSlurper.parseText(response.getContent())
        response        = null
        jsonSlurper     = null

        if(resp.message != null)
        {
            steps.echo "Resp: " + resp.message
            error
        }
        else
        {
            def taskList = resp.tasks

            taskList.each
            {
                if(it.moduleType == 'COB' && it.level == level)
                {
                    returnList.add(it.taskId)
                }
            }
        }

        return returnList
    
    }

/* 
    Receive a response from an "Get Tasks in Set"-httpRequest and build and return a list of TaskAsset Objects that belong to the desired level
*/
    def ArrayList getSetTaskList(ResponseContentSupplier response, String level)
    {

        def jsonSlurper         = new JsonSlurper()

        int ispwTaskCounter     = 0

        def returnList  = []

        def resp = jsonSlurper.parseText(response.getContent())

        if(resp.message != null)
        {
            steps.echo "Resp: " + resp.message
            error
        }
        else
        {
            def taskList = resp.tasks

            taskList.each
            {
                if(it.moduleType == 'COB' && it.level == level)
                {
                    returnList[ispwTaskCounter]             = new TaskInfo()                    
                    returnList[ispwTaskCounter].programName = it.moduleName
                    returnList[ispwTaskCounter].ispwTaskId  = it.taskId

                    ispwTaskCounter++
                }
            }
        }

        return returnList
    
    }

/* 
    Receive a response from an "Get Tasks in Set"-httpRequest and return the List of Releases
*/
    def ArrayList getSetRelease(ResponseContentSupplier response)
    {
        def jsonSlurper = new JsonSlurper()
        def returnList  = []
        def resp        = jsonSlurper.parseText(response.getContent())

        if(resp.message != null)
        {
            echo "Resp: " + resp.message
            error
        }
        else
        {
            def taskList = resp.tasks

            taskList.each
            {
                if(it.moduleType == 'COB')
                {
                    returnList.add(it.release)
                }
            }
        }

        return returnList
    
    }

/*
    Receive a list of TaskInfo Objects, the response of an "List tasks of a Release"-httpRequest to build and return a List of TaskInfo Objects
    that contain the base and internal version
*/
    def setTaskVersions(ArrayList tasks, ResponseContentSupplier response, String level)
    {
        def jsonSlurper = new JsonSlurper()

        def resp        = jsonSlurper.parseText(response.getContent())

        def returnList  = []

        if(resp.message != null)
        {
            echo "Resp: " + resp.message
            error
        }
        else
        {
            def taskList = resp.tasks

            for(int i = 0; i < tasks.size(); i++)
            {
                taskList.each
                {
                    if(it.taskId == tasks[i].ispwTaskId && it.level == level)
                    {
                        returnList[i]               = new TaskInfo()
                        returnList[i].programName   = tasks[i].programName
                        returnList[i].baseVersion   = it.baseVersion
                        returnList[i].targetVersion = it.internalVersion
                        returnList[i].ispwTaskId    = tasks[i].ispwTaskId
                    }
                }
            }
        }

        return returnList

    }
}

