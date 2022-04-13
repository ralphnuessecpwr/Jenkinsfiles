String hostConnectionId     = '196de681-04d7-4170-824f-09a5457c5cda'
String hostCredentialsId    = 'ea48408b-b2be-4810-8f4e-5b5f35977eb1'  
String cesHostName          = 'http://cwcc.compuware.com'
String cesPort              = '2020'
String ispwServerName       = 'ispw'
String cesCredentialsId     = '71063193-ee67-4b52-890a-58843f33c183'  
String gitRepoUrl           = 'https://github.com/CPWRGIT/HDDRXM0.git'
String gitCredentialsId     = '67a3fb18-073f-498b-adee-1a3c75192745'  
String mddlType             = 'MDDL'
String jobSubMsgLabel       = 'WZESUB'
String jobNameLabel         = 'JOB'
String jobIdStartLabel      = '('
String jobIdEndLabel        = ')'
String jobDd                = 'JESJCL'
String procTokenLabel       = 'PROCTOKEN'
String taskIdxLabel         = 'TASKIDX'

def taskIdxLength           = 12
def jobNameLength           = 8
def procTokenLength         = 16

String cesUrl

def compileTaskInfoList
def mddlTaskList

node {
    stage("Initialize"){

        initialize()

        compileTaskInfoList = getCompileTaskInfoList()
        mddlTaskList    = getMddlTaskList(taskList)

println compileTaskInfoList.toString()
println mddlTaskList.toString()

    }
}

def initialize() {

    ispwSetId   = ispwSetId
    cesUrl      = cesHostName + ':' + cesPort
    
}

def getCompileTaskInfoList() {

    def compileTaskInfoList     = []
    def setLog                  = getSetLog(ispwSetId)
    def setJobList              = getSetJobList(setLog)

    setJobList.each{

        def jobLog          = getJobLog(it)
        def compileTaskInfo = getCompileTaskInfo(jobLog)
        
        compileTaskInfoList.add(compileTaskInfo)

    }

    return compileTaskInfoList
}

def getRestMessageContent(url){

    def response

    withCredentials(
        [
            string(
                credentialsId: cesCredentialsId, 
                variable: 'cesToken'
            )
        ]
    ) {
        response = httpRequest(
            url:                    url, 
            contentType:            'APPLICATION_JSON',
            acceptType:             'APPLICATION_JSON', 
            customHeaders:          [
                    [maskValue: true, name: 'authorization', value: cesToken]
                ],
            responseHandle:         'NONE', 
            consoleLogResponseBody: false, 
            wrapAsMultipart:        false
        )
    }
    
    return readJSON(text: response.content).message

}

def getSetLog(ispwSetId) {

    def setLogName  = 'SYS2.ISPW.SX.' + ispwSetId.substring(0,4) + '.%23' + ispwSetId.substring(4,10) + '.LOG'
    def url         = cesUrl + '/compuware/ws/topazapi/datasets/' + setLogName
    
    def fileContent = getRestMessageContent(url)

    return fileContent

}

def getSetJobList(setLog) { 

    def jobName         = ''    
    def jobId           = ''
    def records         = setLog.split('\n')
    def setJobList      = []

    records.each{

        if(it.indexOf(jobSubMsgLabel) >= 0){

            def jobNameStartLoc = it.indexOf(jobNameLabel) + jobNameLabel.length() + 1
            def jobNameEndLoc   = jobNameStartLoc + jobNameLength
            def jobIdStartLoc   = it.indexOf(jobIdStartLabel) + 1
            def jobIdEndLoc     = it.indexOf(jobIdEndLabel)
            def jobInfo         = [:]

            jobName             = it.substring(jobNameStartLoc, jobNameEndLoc)
            jobId               = it.substring(jobIdStartLoc, jobIdEndLoc)

            jobInfo.name        = jobName
            jobInfo.id          = jobId

            setJobList.add(jobInfo)
        }
    }

    return setJobList
}

def getJobLog(jobInfo){

    def jobName = jobInfo.name
    def jobId   = jobInfo.id
    def url     = cesUrl + '/compuware/ws/topazapi/jes/' + jobName + '/' + jobId + '/' + jobDd + '/content' 

    def jobLog  = getRestMessageContent(url)

    return jobLog

}

def getCompileTaskInfo(jobLog) {

    def compileTaskInfo         = [:]

    def procTokenStartLoc       = jobLog.indexOf(procTokenLabel) + procTokenLabel.length() + 1
    def procTokenEndLoc         = procTokenStartLoc + procTokenLength
    def procToken               = jobLog.substring(procTokenStartLoc, procTokenEndLoc)

    def taskIdxStartLoc         = jobLog.indexOf(taskIdxLabel) + taskIdxLabel.length() + 1
    def taskIdxEndLoc           = taskIdxStartLoc + taskIdxLength
    def taskIdx                 = jobLog.substring(taskIdxStartLoc, taskIdxEndLoc)

    compileTaskInfo.taskIdx     = taskIdx
    compileTaskInfo.procToken   = procToken

    return compileTaskInfo
}

def getTaskList(ispwSetId) {

    def response    = ispwOperation(
            connectionId:           hostConnectionId,
            credentialsId:          cesCredentialsId,   
            consoleLogResponseBody: true, 
            ispwAction:             'GetSetTaskList', 
            ispwRequestBody:        'ispwSetId=' + ispwSetId
        )

    def taskList        = readJSON(text: response.content).tasks

    return taskList
}

def getMddlTaskList(taskList) {
    
    taskList        = getTaskList(ispwSetId)
    def mddlTaskList    = []

    taskList.each() {
        if(it.moduleType == mddlType){
            mddlTaskList.add(it)
        }
    }

    return mddlTaskList
}