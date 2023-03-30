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

        echo "Calling Mddl_Checkout_Pipeline with parameters\n\n" +
            "execParms: " + execParms.toString() + "\n\n" +
            "pipelineConfig: " + pipelineConfig.toString() + "\n\n" +
            "mddlTaskList: " + mddlTaskList.toString() + "\n\n" +
            "ispwSourceLevel: "  + ispwSourceLevel + "\n\n" + 
            "ispwTargetLevel: " + ispwTargetLevel + "\n\n" + 
            "cesUrl: " + cesUrl
            
        Mddl_Checkout_Pipeline(execParms, pipelineConfig, mddlTaskList, ispwSourceLevel, ispwTargetLevel, cesUrl)
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

    ispwTargetLevel     = ispwLevel
    ispwSourceLevel     = determineCheckoutFromLevel(mddlTaskList)

    currentBuild.displayName = "Table Checkout for user ${ispwOwner} from ${ispwSourceLevel}"

}

def getTaskList(ispwSetId) {

    def response    = ispwOperation(
                            connectionId:           pipelineConfig.host.connectionId, 
                            credentialsId:          pipelineConfig.ces.credentialsId,
                            // serverConfig:           pipelineConfig.ispw.runtimeConfig, 
                            consoleLogResponseBody: true, 
                            ispwAction:             'GetSetTaskList', 
                            ispwRequestBody:        '''setId=''' + ispwSetId + '''
                                runtimeConfiguration=''' + pipelineConfig.ispw.runtimeConfig
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
    def cesToken

    withCredentials(
        [
            string(
                credentialsId: pipelineConfig.ces.credentialsId, 
                variable: 'tmpToken'
            )
        ]
    )
    {
        cesToken = tmpToken
    }

    def response = httpRequest(
            consoleLogResponseBody: true, 
            customHeaders: [
                [maskValue: false, name: 'authorization', value: '665fc9fb-39de-428a-8a67-a3619752873d'],
                [maskValue: false, name: 'content-type', value: 'application/json']
            ],
            url: pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port +'/ispw/' + pipelineConfig.ispw.runtimeConfig + '/componentVersions/list?application=' + ispwApplication + '&mname=' + mddlTaskList[0].moduleName + '&mtype=MDDL', 
            wrapAsMultipart: false
        )

    def jsonSlurper = new JsonSlurper()
    def resp        = jsonSlurper.parseText(response.getContent())

    response        = null
    jsonSlurper     = null

    def versions    = resp.componentVersions

    def baseVersion

    versions.each {

        if (it.assignmentId == ispwAssignmentId) {

            baseVersion = it.baseVersion
            echo "Found Base Version: " + baseVersion
        }
    }

    if(baseVersion == null){

        error "Could not determine base version. Abort build."
    }
    
    def fromLevel 

    versions.each {

        if (it.internalVersion == baseVersion) {

            fromLevel = it.level
            echo "Found From Level: " + fromLevel
        }
    }

    if(fromLevel == null){

        error "Could not determine from level. Abort build."
    }

    return fromLevel
}