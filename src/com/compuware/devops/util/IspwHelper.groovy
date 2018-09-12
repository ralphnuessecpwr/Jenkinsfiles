package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*

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
    Receive a response from an "Get Tasks in Set"-httpRequest and build a list of task IDs
*/
/* 
    "@NonCPS" is required to tell Jenkins that the objects in the method do not need to survive a Jenkins re-start
    This is necessary because JsonSlurper uses non serializable classes, which will leed to exceptions when not used
    in methods in a @NonCPS section 
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
    Receive a list of task IDs and the response of an "List tasks of a Release"-httpRequest to build a list of Assignments
    that are contained in both the Task Id List and the List of Tasks in the Release 
*/
/* 
    "@NonCPS" is required to tell Jenkins that the objects in the method do not need to survive a Jenkins re-start
    This is necessary because JsonSlurper uses non serializable classes, which will leed to exceptions when not used
    in methods in a @NonCPS section 
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
    Receive a response from an "Get Tasks in Set"-httpRequest and return the Release
*/
/* 
    "@NonCPS" is required to tell Jenkins that the objects in the method do not need to survive a Jenkins re-start
    This is necessary because JsonSlurper uses non serializable classes, which will leed to exceptions when not used
    in methods in a @NonCPS section 
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
    Receive a list of task IDs and the response of an "List tasks of a Release"-httpRequest to build a list of Assignments
    that are contained in both the Task Id List and the List of Tasks in the Release 
*/
/* 
    "@NonCPS" is required to tell Jenkins that the objects in the method do not need to survive a Jenkins re-start
    This is necessary because JsonSlurper uses non serializable classes, which will leed to exceptions when not used
    in methods in a @NonCPS section 
*/
@NonCPS
    def ArrayList getAssigmentList(ArrayList taskIds, ResponseContentSupplier response)
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
    Receive a list of task IDs and the response of an "List tasks of a Release"-httpRequest to build a Map of Programs and Base Versions
*/
/* 
    "@NonCPS" is required to tell Jenkins that the objects in the method do not need to survive a Jenkins re-start
    This is necessary because JsonSlurper uses non serializable classes, which will leed to exceptions when not used
    in methods in a @NonCPS section 
*/
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

}