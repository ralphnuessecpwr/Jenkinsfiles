package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*

class IspwHelper implements Serializable {

    def steps
    
    IspwHelper(steps) 
    {
        this.steps = steps
    }

    def ArrayList getSetTaskIdList(ResponseContentSupplier response, String level)
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
                if(it.moduleType == 'COB' && it.level == level)
                {
                    returnList.add(it.taskId)
                }
            }
        }

        return returnList
    
    }

}
