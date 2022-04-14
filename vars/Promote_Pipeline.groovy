String configFile
String ispwCurrentLevel
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

            if(mddlTaskList.size() > 1) {

                error "At the current stage we do not support processing of more than one MDDL member per build."

            }
        }

        parallel(

            processMddl: {
                if(mddlTaskList.size() > 0) {
                    node {
                        Mddl_Pipeline(execParms, pipelineConfig, mddlTaskList, cesUrl)
                    }
                }
            },
            processCobol: {
                if(cobTaskList.size() > 0) {
                    node {
                        Cobol_Pipeline(execParms, pipelineConfig, ispwCurrentLevel, cesUrl)
                    }
                }
            }

        )

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
    cobTaskList         = getTaskListsByType(taskList)[0]
    mddlTaskList        = getTaskListsByType(taskList)[1]

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

    taskListBytype.add(cobTaskList)
    taskListByType.add(mddlTaskList)

    return taskListByType
}
