#!/usr/bin/env groovy
import groovy.json.JsonSlurper
import com.compuware.devops.config.*
import com.compuware.devops.jclskeleton.*
import com.compuware.devops.util.*

def call(Map pipelineParams)
{
    node
    {
        stage("Initialization")
        {
            def cesToken = "665fc9fb-39de-428a-8a67-a3619752873d"

            def assignment = "RXN3000022"

            def taskList = ['CWXTCOB','CWXTDATE','CWXTSUBC']

            taskList.each
            {
                def jsonBody = '''{
                    "runtimeConfiguration": "ispw",
                    "containerId": "RXN3REL02",
                    "containerType": "R"
                }'''

                httpRequest(
                    httpMode:                   'POST',
                    url:                        "http://cwcc.compuware.com:2020/ispw/ispw/assignments/RXN3000022/tasks/transfer?${it}",
                    consoleLogResponseBody:     true, 
                    requestBody:                jsonBody,
                    customHeaders:              [[
                                                maskValue:  true, 
                                                name:       'authorization', 
                                                value:      "${cesToken}"
                                                ],
                                                [
                                                maskValue: false, 
                                                name: 'content-type', 
                                                value: 'application/json'
                                                ]]
                    
                )

            }
        }
    }
}