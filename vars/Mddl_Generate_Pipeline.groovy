String configFile
String ispwCurrentLevel
String cesUrl

def pipelineConfig
def mddlTaskList
def mddlTaskInfoList

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

            mddlTaskInfoList = getMddlTaskInfoList()
            println mddlTaskInfoList.toString()

        }
    }
}

def initialize(execParms) {

    dir("./") 
    {
        deleteDir()
    }

    pipelineConfig      = readYaml(text: libraryResource(configFile))
    ispwStream          = execParms.ispwStream
    ispwApplication     = execParms.ispwApplication
    ispwSetId           = execParms.ispwSetId
    ispwLevel           = execParms.ispwLevel
    cesUrl              = pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port
    ispwCurrentLevel    = pipelineConfig.ispw.lifeCycle[ispwLevel]

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
            targetFolder:       pipelineConfig.ispw.mddlRootFolder,
            ispwDownloadAll:    false, 
            ispwDownloadIncl:   false, 
        ]
    )

}

def getMddlTaskInfoList() {

    def mddlTaskInfoList    = [:]

    mddlTaskList.each {

        def mddlTaskInfo    = [:]
        def mddlFileName    = it.moduleName + '.' + it.moduleType
        def mddlPath        = pipelineConfig.ispw.mddlRootFolder + '/' + ispwApplication + '/' + pipelineConfig.ispw.mddlFolder
        def mddlContent     = readFile(file: mddlPath + '/' + mddlFileName)
        def records         = mddlContent.split('\n')

        records.each {

            if(it.charAt(0) != pipelineConfig.mddl.commentMarker) {

                def key     = it.split(pipelineConfig.mddl.valueMarker)[0]
                def value   = it.split(pipelineConfig.mddl.valueMarker)[1]
                
                if(pipelineConfig.mddl.keywords.contains(key)) {
                    mddlTaskInfo[key] = value
                }
                else {
                    error "The MDDL Member contained unknown keyword: " + key
                }                
            }
        }
println mddlTaskInfo.toString()
println "Task ID: " it.taskId.toString()
        mddlTaskInfoList[it.taskId] = mddlTaskInfo
println "Task InfoList: " mddlTaskInfoList.toString()
    }
}