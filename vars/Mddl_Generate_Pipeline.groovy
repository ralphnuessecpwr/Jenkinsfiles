//import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
//import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
//import hudson.model.Result;

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

        }

        stage('Compare Developer Schema definition against Production') {

            def mddlTaskInfo = mddlTaskInfoList[0]
            println mddlTaskInfo.toString()

            withCredentials(
                [   
                    usernamePassword(
                        credentialsId: pipelineConfig.host.credentialsId, 
                        usernameVariable: 'hostUser',
                        passwordVariable: 'hostPasword'
                    )
                ]
            ) {

                build(
                    job:        'Compare_DDL',
                    parameters: [
                        string(name:    'jobId',       value: "x1234"),
                        string(name:    'fromSsid',    value: mddlTaskInfo.DB2SSID),
                        string(name:    'toSsid',      value: mddlTaskInfo.DB2PSSID),
                        string(name:    'dbName',      value: mddlTaskInfo.DB2DB),
                        string(name:    'tsoUser',     value: hostUser),
                        password(name:  'tsoPassword', value: hostPassword)
                    ]
                )
            }
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
/*
    bmcCN.reset("BMC_SCHEMA_IDENTICAL")
    bmcCN.reset("BMC_GENERATE_JCL_ONLY")
    bmcCN.reset("BMC_SKIP_CDL_GENERATION")
    bmcCN.reset("BMC_RESET_RC")
	
	bmcSM.reset("BMC_SCHEMA_IDENTICAL")
    bmcSM.reset("BMC_GENERATE_JCL_ONLY")
    bmcSM.reset("BMC_SKIP_CDL_GENERATION")
    bmcSM.reset("BMC_RESET_RC")
*/
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

        def mddlFileName    = it.moduleName + '.' + it.moduleType
        def mddlPath        = pipelineConfig.ispw.mddlRootFolder + '/' + ispwApplication + '/' + pipelineConfig.ispw.mddlFolder
        def mddlContent     = readFile(file: mddlPath + '/' + mddlFileName)
        def records         = mddlContent.split('\n')
        def mddlTaskInfo    = getMddlTaskInfo(records)

        mddlTaskInfoList[it.taskId] = mddlTaskInfo
    
    }

    return mddlTaskInfoList
}

def getMddlTaskInfo(records) {

    def mddlTaskInfo    = [:]

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

    return mddlTaskInfo
}