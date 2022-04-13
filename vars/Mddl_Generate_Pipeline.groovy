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

            if(mddlTaskList.size() > 1) {

                error "At the current stage we do not support processing of more than one MDDL member per build."

            }
        }

        stage("Retrieve MDDL members") {

            downloadMddlMembers()

        }

        stage("Process MDDL members") {

            processMddlFiles()

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

    echo "URL " + cesUrl
    echo "Levl " + ispwCurrentLevel

    def taskList        = getTaskList(ispwSetId)
    mddlTaskList        = getMddlTaskList(taskList)

}

def getTaskList(ispwSetId) {

    def response    = ispwOperation(
                            connectionId:           pipelineConfig.host.connectionId, 
                            credentialsId:          pipelineConfig.ces.credentialsId,
                            serverConfig:           pipelineConfig.ispw.runtimeConfig, 
                            consoleLogResponseBody: true, 
                            ispwAction:             'GetSetTaskList', 
                            ispwRequestBody:        'setId=' + ispwSetId
                        )

    def taskList        = readJSON(text: response.content).tasks

    return taskList
}

def getMddlTaskList(taskList) {
    
    def mddlTaskList    = []

    taskList.each() {

        if(it.moduleType == pipelineConfig.ispw.mddlType){
            mddlTaskList.add(it)
        }

    }

    return mddlTaskList
}

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
            ispwDownloadAll:    false, 
            ispwDownloadIncl:   false, 
        ]
    )

}

def processMddlFiles() {

    mddlTaskList.each {

        def mddlFileName    = it.moduleName + '.' + it.moduleType
        def mddlPath        = ispwApplication + '/' + pipelineConfig.ispw.mddlFolder

        def mddlContent     = readFile(file: mddlPath + '/' + mddlFileName)

        println mddlContent
    
    }
}