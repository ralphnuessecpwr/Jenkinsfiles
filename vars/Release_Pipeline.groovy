import groovy.json.JsonSlurper

String configFile
String ispwSourceLevel
String ispwTargetLevel
String cesUrl
def pipelineConfig
def symdir
def mddlTaskList
// def cobTaskList

def call(Map execParms)
{
    configFile  = 'mddlPipeline.yml'

    node {

        stage("Initialize"){

            initialize(execParms)

        }

        echo "Calling Mddl_Promote_Pipeline with parameters\n\n" +
            "execParms: " + execParms.toString() + "\n\n" +
            "pipelineConfig: " + pipelineConfig.toString() + "\n\n" +
            "mddlTaskList: " + mddlTaskList.toString() + "\n\n" +
            "ispwCurrentLevel: "  + ispwCurrentLevel + "\n\n" + 
            "db2SourceLevel: "  + db2SourceLevel + "\n\n" + 
            "db2TargetLevel: " + db2TargetLevel + "\n\n" + 
            "cesUrl: " + cesUrl

        Mddl_Release_Pipeline(execParms, pipelineConfig, mddlTaskList, ispwCurrentLevel, db2SourceLevel, db2TargetLevel, cesUrl)
    }
}

def initialize(execParms) {

    cleanWs()

    pipelineConfig      = readYaml(text: libraryResource(configFile))

    ispwStream          = execParms.ispwStream
    ispwApplication     = execParms.ispwApplication
    ispwAssignmentId    = execParms.ispwAssignmentId
    ispwSetId           = execParms.ispwSetId
    ispwOwner           = execParms.ispwOwner
    ispwLevel           = execParms.ispwLevel
    cesUrl              = pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port

    def taskList        = getTaskList(ispwSetId)
    cobTaskList         = getTaskListsByType(taskList)[0]
    mddlTaskList        = getTaskListsByType(taskList)[1]

    if(mddlTaskList.size() > 1) {

        error "At the current stage we do not support processing of more than one MDDL member per build."

    }
    else if(mddlTaskList.size() == 0) {
        error "No MDDL Task was found."
    }

    ispwCurrentLevel    = ispwLevel
    db2SourceLevel      = pipelineConfig.ispw.lifeCycle[ispwLevel].db2SourceLevel
    db2TargetLevel      = pipelineConfig.ispw.lifeCycle[ispwLevel].db2TargetLevel    

    //currentBuild.displayName = "Change at ${db2SourceLevel} to ${db2TargetLevel}"
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