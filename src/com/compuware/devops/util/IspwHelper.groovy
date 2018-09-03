package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*

class IspwHelper implements Serializable {

    def steps
    def String ispwUrl
    def String ispwRuntime
    def String ispwContainer
    def String cesToken
    
    IspwHelper(steps, String ispwUrl, String ispwRuntime, String ispwContainer, String cesToken) 
    {

        this.steps          = steps
        this.ispwUrl        = ispwUrl
        this.ispwRuntime    = ispwRuntime
        this.ispwContainer  = ispwContainer
        this.cesToken       = cesToken

    }

@NonCPS
    def ArrayList getSetTaskIdList(String level)
    {
        def jsonSlurper         = new JsonSlurper()
        def httpRequestWrapper  = new HttpRequestWrapper(steps)

        def returnList  = []

        steps.echo "Before httpRequest"

        def response = httpRequestWrapper.httpGet("${ispwUrl}/ispw/${ispwRuntime}/sets/${ispwContainer}/tasks", cesToken)

        steps.echo "After httpRequest"

        def resp = jsonSlurper.parseText(response.getContent())

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
                if(it.moduleType == 'COB' && it.level == level)
                {
                    returnList.add(it.taskId)
                }
            }
        }

        returnList.each{
            echo "returnList: " it.toString()
        }

        return returnList
    
    }

}
