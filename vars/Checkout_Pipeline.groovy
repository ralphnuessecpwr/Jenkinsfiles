import groovy.json.JsonSlurper

String configFile
String ispwSourceLevel
String ispwTargetLevel
String cesUrl
def pipelineConfig
def symdir
def mddlTaskList
def cobTaskList

def execParms = [:]
execParms.ispwStream        = ispwStream
execParms.ispwApplication   = ispwApplication
execParms.ispwSetId         = ispwSetId
execParms.ispwAssignmentId  = ispwAssignmentId
execParms.ispwLevel         = ispwLevel
execParms.ispwEvent         = ispwEvent

call(execParms)

def call(Map execParms)
{
    configFile  = 'mddlPipeline.yml'

    node {

        stage("Initialize"){

            initialize(execParms)

        }

        echo "Would call Mddl_Checkout_Pipeline with parameters\n\n" +
            "execParms: " + execParms.toString() + "\n\n" +
            "pipelineConfig: " + pipelineConfig.toString() + "\n\n" +
            "mddlTaskList: " + mddlTaskList.toString() + "\n\n" +
            "ispwSourceLevel: "  + ispwSourceLevel + "\n\n" + 
            "ispwTargetLevel: " + ispwTargetLevel + "\n\n" + 
            "cesUrl: " + cesUrl
            
        //Mddl_Checkout_Pipeline(execParms, pipelineConfig, mddlTaskList, ispwSourceLevel, ispwTargetLevel, cesUrl)
    }
}

def initialize(execParms) {

    cleanWs()

    //pipelineConfig      = readYaml(text: libraryResource(configFile))

    pipelineConfig      = buildIspwConfig()
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

    ispwTargetLevel     = ispwLevel
    ispwSourceLevel     = determineCheckoutFromLevel(mddlTaskList)
}

def buildIspwConfig() {
    def config  = [:]
    config.host = [:]
    config.host.connectionId    = '72b71d9e-3509-4455-862e-b5bd48deb87d'
    config.host.credentialsId   = 'HDDRXM0'
    config.host.name            = '192.168.96.130'
    config.host.zosmfPort       = '443'

    config.amiDevOps = [:]
    config.amiDevOps.credentialsId  = '58dZsyU/YmUVLxyB29AkqooNkcG1E8pa 57,#-6,#30,#63,1Sa2QQ=='
    config.amiDevOps.symDir         = 'AMI_DevOps_CWCC.properties'
    config.amiDevOps.analysisIn     = 'skels/ami/analysisInput.skel'
    config.amiDevOps.compIn         = 'skels/ami/compareInput.skel'
    config.amiDevOps.importIn       = 'skels/ami/importInput.skel'
    config.amiDevOps.jclGenIn       = 'skels/ami/jclGenInput.skel'
    config.amiDevOps.jcl            = 'skels/ami/compareJcl.skel'
    config.amiDevOps.jobcard        = 'skels/ami/jobcard.skel'
    config.amiDevOps.outputFolder   = 'AMI_Output'
    config.amiDevOps.datasetNames = [:]
    config.amiDevOps.datasetNames.work = [:]
    config.amiDevOps.datasetNames.work.wlistpds     = 'HDDRXM0.AMI.DEVOPS.WORKLIST'
    config.amiDevOps.datasetNames.work.cdlpds       = 'HDDRXM0.AMI.DEVOPS.CDL'
    config.amiDevOps.datasetNames.work.execjclpds   = 'HDDRXM0.AMI.DEVOPS.EXECJCL'
    config.amiDevOps.datasetNames.work.importpds    = 'HDDRXM0.AMI.DEVOPS.IMPRPT'
    config.amiDevOps.datasetNames.rollback = [:] 
    config.amiDevOps.datasetNames.rollback.wlistpds = 'HDDRXM0.AMI.ROLLBK.WORKLIST'
    config.amiDevOps.datasetNames.rollback.cdlpds   = 'HDDRXM0.AMI.ROLLBK.CDL'
    config.amiDevOps.datasetNames.rollback.execjclpds   = 'HDDRXM0.AMI.ROLLBK.EXECJCL'
    config.amiDevOps.datasetNames.rollback.importpds    = 'HDDRXM0.AMI.DEVOPS.IMPRPT'
    config.ces = [:]
    config.ces.hostName = 'http://192.168.96.130'
    config.ces.port = '2020'
    config.ces.credentialsId = 'HDDRXM0_CES_CWCC'
    config.ispw = [:]
    config.ispw.serverName          = 'ispw'
    config.ispw.runtimeConfig       = 'iccga'
    config.ispw.containerTypeSet    = '2'
    config.ispw.mddlType            = 'MDDL'
    config.ispw.mddlRootFolder      = 'Mddl'
    config.ispw.cobType             = 'COB'
    config.ispw.cobRootFolder       = 'Cobol'
    config.ispw.fileFolder          = 'MF_Source'
    config.ispw.lifeCycle = [:]
    config.ispw.lifeCycle.UT    = 'CONS'
    config.ispw.lifeCycle.CONS  = 'ST'
    config.ispw.lifeCycle.ST    = 'AT'
    config.ispw.lifeCycle.AT    = 'PRD'
    config.mddl = [:]
    config.mddl.commentMarker = '#'
    config.mddl.valueMarker = '='
    config.mddl.keywords = []
    config.mddl.keywords.add('DB2SSID')
    config.mddl.keywords.add('DB2DB')
    config.mddl.keywords.add('DB2TS')
    config.mddl.keywords.add('DB2PSSID')
    config.mddl.keywords.add('DB2PDB')
    config.mddl.keywords.add('DB2PTS')
    config.mddlTemplate = [:]
    config.mddlTemplate.source = [:]
    config.mddlTemplate.source.database = 'HDDRXMDB'
    config.mddlTemplate.source.tablespace = 'RXNTABT'
    config.mddlTemplate.source.table = ""
    config.mddlTemplate.target = [:]
    config.mddlTemplate.target.database = 'TOPTOTDB'
    config.mddlTemplate.target.tablespace = 'TOPTOTTS'
    config.mddlTemplate.target.table = ""

    return config
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

    return fromLevel
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
            serverLevel:        ispwLevel,
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