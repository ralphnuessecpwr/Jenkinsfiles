String hostConnectionId     = '196de681-04d7-4170-824f-09a5457c5cda'
String hostCredentialsId    = 'ea48408b-b2be-4810-8f4e-5b5f35977eb1'  
String cesHostName          = 'http://cwcc.compuware.com'
String cesPort              = '2020'
String ispwServerName       = 'ispw'
String ispwRuntimeConfig    = 'ispw'
String ispwSetContainerType = '2'
String cesCredentialsId     = '71063193-ee67-4b52-890a-58843f33c183'  
String gitRepoUrl           = 'https://github.com/CPWRGIT/HDDRXM0.git'
String gitCredentialsId     = '67a3fb18-073f-498b-adee-1a3c75192745'  
String mddlType             = 'MDDL'
String mddlFolder           = 'Mddl'

def ispwLifeCycle           = ['UT': 'CONS', 'CONS': 'ST', 'ST': 'AT', 'AT': 'PRD']

String ispwCurrentLevel
String cesUrl

def compileTaskInfoList
def mddlTaskList

def call(Map execParms)
{
    node {
        stage("Initialize"){

            initialize(execParms)

            compileTaskInfoList = getCompileTaskInfoList()
            mddlTaskList    = getMddlTaskList(taskList)

    println compileTaskInfoList.toString()
    println mddlTaskList.toString()

        }
    }
}

def initialize(execParms) {

    ispwStream      = execParms.ispwStream
    ispwApplication = execParms.ispwApplication
    ispwSetId       = execParms.ispwSetId
    ispwLevel       = execParms.ispwLevel
    cesUrl          = cesHostName + ':' + cesPort
    
}

/*
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
*/
def downloadMddlMembers() {
    
    checkout(
        changelog: false, 
        poll: false, 
        scm: [
            $class:             'IspwContainerConfiguration', 
            componentType:      mddlType, 
            connectionId:       hostConnectionId, 
            serverConfig:       ispwRuntimeConfig, 
            credentialsId:      hostCredentialsId, 
            containerName:      ispwSetId, 
            containerType:      ispwSetContainerType, 
            serverLevel:        ispwCurrentLevel, 
            targetFolder:       mddlFolder,
            ispwDownloadAll:    false, 
            ispwDownloadIncl:   false, 
        ]
    )
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
    
    def mddlTaskList    = []

    taskList.each() {

        if(it.moduleType == mddlType){
            mddlTaskList.add(it)
        }

    }

    return mddlTaskList
}