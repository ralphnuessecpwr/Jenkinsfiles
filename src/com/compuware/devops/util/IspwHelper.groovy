package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*
import com.compuware.devops.util.TaskInfo

class IspwHelper implements Serializable 
{

    def steps
    def String ispwUrl
    def String ispwRuntime
    def String ispwContainer
    
    IspwHelper(steps, String ispwUrl, String ispwRuntime, String ispwContainer)
    {

        this.steps          = steps
        this.ispwUrl        = ispwUrl
        this.ispwRuntime    = ispwRuntime
        this.ispwContainer  = ispwContainer

    }

/* 
    "@NonCPS" is required to tell Jenkins that the objects in the method do not need to survive a Jenkins re-start
    This is necessary because JsonSlurper uses non serializable classes, which will leed to exceptions when not used
    in methods in a @NonCPS section 
*/

/* 
    Receive a response from an "Get Tasks in Set"-httpRequest and build and return a list of task IDs that belong to the desired level
*/
@NonCPS
    def ArrayList getSetTaskIdList(ResponseContentSupplier response, String level)
    {
        def jsonSlurper         = new JsonSlurper()

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
                    returnList.add(it.taskId)
                }
            }
        }

        return returnList
    
    }

/* 
    Receive a response from an "Get Tasks in Set"-httpRequest and build and return a list of TaskAsset Objects that belong to the desired level
*/
@NonCPS
    def ArrayList getSetTaskList(ResponseContentSupplier response, String level)
    {
        steps.echo "getSetTaskList"
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

        steps.echo "Return: " + returnList.toString()
        return returnList
    
    }


/* 
    Receive a list of task IDs and the response of an "List tasks of a Release"-httpRequest to build and return a list of Assignments
    that are contained in both the Task Id List and the List of Tasks in the Release 
*/
@NonCPS
    def ArrayList getAssigmentList(ArrayList taskIds, ResponseContentSupplier response)
    {
        def jsonSlurper = new JsonSlurper()
        def returnList  = []

        def resp        = jsonSlurper.parseText(response.getContent())

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
    Receive a response from an "Get Tasks in Set"-httpRequest and return the List of Releases
*/
@NonCPS
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
    Receive a list of task IDs and the response of an "List tasks of a Release"-httpRequest to build a Map of Programs and Base Versions
*/
/*
@NonCPS
    def Map getProgramVersionMap(ArrayList taskIds, ResponseContentSupplier response)
    {
        def jsonSlurper = new JsonSlurper()
        def returnMap  = [:]

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
                if(taskIds.contains(it.taskId))
                {
                    echo "Add to programVersionMap: " + it.moduleName + " : " + it.baseVersion
                    returnMap.put(it.moduleName, it.baseVersion)
                }
            }
        }

        return returnMap    
    }

*/
/*
    Receive a list of TaskInfo Objects, the response of an "List tasks of a Release"-httpRequest to build and return a List of TaskInfo Objects
    that contain the base and internal version
*/
@NonCPS
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

