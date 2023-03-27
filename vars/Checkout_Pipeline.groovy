String configFile
String ispwCurrentLevel
String ispwSourceLevel
String ispwTargetLevel
String cesUrl
def pipelineConfig
def symdir
def mddlTaskList
def cobTaskList

def call(Map execParms)
{
    configFile  = 'mddlPipeline.yml'

    node {

        stage("Initialize"){

            initialize(execParms)

        }

        echo "Would call Mddl_Checkout_Pipeline with parameters\n" +
            "execParms: " + execParms.toString() + "\n" +
            "pipelineConfig: " + pipelineConfig.toString() + "\n" +
            "mddlTaskList: " + mddlTaskList.toString() + "\n" +
            "ispwSourceLevel: "  + ispwSourceLevel + "\n" + 
            "ispwTargetLevel: " + ispwTargetLevel + "\n" + 
            "cesUrl: " + cesUrl
            
        //Mddl_Checkout_Pipeline(execParms, pipelineConfig, mddlTaskList, ispwSourceLevel, ispwTargetLevel, cesUrl)
    }
}

def initialize(execParms) {

    cleanWs()

    pipelineConfig      = readYaml(text: libraryResource(configFile))
    ispwStream          = execParms.ispwStream
    ispwApplication     = execParms.ispwApplication
    ispwSetId           = execParms.ispwSetId
    ispwLevel           = execParms.ispwLevel
    cesUrl              = pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port

    def taskList        = getTaskList(ispwSetId)
    cobTaskList         = getTaskListsByType(taskList)[0]
    mddlTaskList        = getTaskListsByType(taskList)[1]

    if(mddlTaskList.size() > 1) {

        error "At the current stage we do not support processing of more than one MDDL member per build."

    }

    ispwTargetLevel     = ispwCurrentLevel
    ispwSourceLevel     = determineCheckoutFromLevel(mddlTaskList)
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

def getTaskListsByType(taskList) {
    
    def taskListsByType = []
    def mddlTaskList    = []
    def cobTaskList     = []

    taskList.each() {

        if(it.moduleType == pipelineConfig.ispw.mddlType){
            mddlTaskList.add(it)
        }
        else if(it.moduleType == pipelineConfig.ispw.cobType){
            cobTaskList.add(it)
        }

    }

    taskListsByType.add(cobTaskList)
    taskListsByType.add(mddlTaskList)

    return taskListsByType
}

def determineCheckoutFromLevel(mddlTaskList) {

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

def getMddlTaskContentList() {

    def mddlTaskContentList    = []

    mddlTaskList.each {

        def mddlFileName                = it.moduleName + '.' + it.moduleType
        def mddlPath                    = pipelineConfig.ispw.mddlRootFolder + '/' + ispwApplication + '/' + pipelineConfig.ispw.fileFolder
        def mddlTaskContent             = readYaml(file: mddlPath + '/' + mddlFileName)

        mddlTaskContent['taskId']       = it.taskId
        mddlTaskContent['moduleName']   = it.moduleName
        mddlTaskContent['userId']       = it.userId
        
        mddlTaskContentList.add(mddlTaskContent)
    
    }

    return mddlTaskContentList
}