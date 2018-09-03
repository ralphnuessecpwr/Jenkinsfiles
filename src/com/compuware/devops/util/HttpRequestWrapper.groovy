package com.compuware.devops.util

import jenkins.plugins.http_request.*

class HttpRequestWrapper implements Serializable {

    def steps
    
    HttpRequestWrapper(steps) 
    {
        this.steps = steps
    }

    def ResponseContentSupplier httpGet(String url, String token)
    {
        return steps.httpRequest(url: "${url}",
            httpMode: 'GET',
            consoleLogResponseBody: false,
            customHeaders: [[maskValue: true, name: 'authorization', value: "${token}"]]
        )
    }
}
