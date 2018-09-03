package com.compuware.devops.util

class HttpRequestWrapper implements Serializable {

    def steps
    
    HttpRequestWrapper(steps) 
    {
        this.steps = steps
    }

    def ResponseContentSupplier echoValue(String url, String token)
    {
        return steps.httpRequest(url: "${url}",
            httpMode: 'GET',
            consoleLogResponseBody: false,
            customHeaders: [[maskValue: true, name: 'authorization', value: "${token}"]]
        )
    }
}
