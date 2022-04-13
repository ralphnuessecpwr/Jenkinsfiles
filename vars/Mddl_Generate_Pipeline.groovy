String configFile
String ispwCurrentLevel
String cesUrl

def pipelineConfig
def mddlTaskList

def call(Map execParms)
{
    configFile = 'mddlPipeline.yml'

    node {

        stage("Initialize"){

            initialize(execParms)

            mddlTaskList    = getMddlTaskList(taskList)

        }

        stage("Retrieve MDDL members") {

            downloadMddlMembers()

        }
    }
}

def initialize(execParms) {

    pipelineConfig      = readYaml(text: libraryResource(configFile))
    ispwStream          = execParms.ispwStream
    ispwApplication     = execParms.ispwApplication
    ispwSetId           = execParms.ispwSetId
    ispwLevel           = execParms.ispwLevel
    
    cesUrl              = pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port
    ispwCurrentLevel    = pipelineConfig.ispw.lifeCycle[ispwLevel]
    
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
            componentType:      pipelineConfig.ispw.mddlType, 
            connectionId:       pipelineConfig.host.connectionId, 
            serverConfig:       pipelineConfig.ispw.runtimeConfig, 
            credentialsId:      pipelineConfig.host.credentialsId, 
            containerName:      ispwSetId, 
            containerType:      pipelineConfig.ispw.containerTypeSet, 
            serverLevel:        ispwCurrentLevel,
            targetFolder:       pipelineConfig.ispw.mddlFolder,
            ispwDownloadAll:    false, 
            ispwDownloadIncl:   false, 
        ]
    )
}

def getTaskList(ispwSetId) {

    def response    = ispwOperation(
                            connectionId:           pipelineConfig.host.connectionId, 
                            serverConfig:           pipelineConfig.ispw.runtimeConfig, 
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