//import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
//import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
//import hudson.model.Result;

String configFile
String ispwCurrentLevel
String cesUrl

def pipelineConfig
def symdir
def mddlTaskList
def mddlTaskContentList

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

        stage("Retrieve MDDL members") {

            downloadMddlMembers()

        }

        stage("Process MDDL members") {

            mddlTaskContentList = getMddlTaskContentList()
            println mddlTaskContentList.toString()
            println mddlTaskContentList[0].toString()

        }

        stage('Compare Developer Schema definition against Production') {

            def mddlTaskContent = mddlTaskContentList[0]
            println mddlTaskContent.toString()

            withCredentials(
                [   
                    usernamePassword(
                        credentialsId: pipelineConfig.host.credentialsId, 
                        usernameVariable: 'hostUser',
                        passwordVariable: 'hostPassword'
                    )
                ]
            ) {
                bmcAmiAuthentication(
                    comtype:    'ZOSMF', 
                    dserver:    pipelineConfig.host.name, 
                    dport:      pipelineConfig.host.zosmfPort,                     
                    duser:      hostUser, 
                    pwdruntime: true,                    
                    dpassrun:   '${hostPassword}', 
                    dpassword:  '',
                    debug:      true, 
                    symdir:     pipelineConfig.amiDevOps.symDir
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

    createAmiDevOpsProperties()

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

def createAmiDevOpsProperties() {

    writeFile(
        file: pipelineConfig.amiDevOps.symDir,
        text: libraryResource(pipelineConfig.amiDevOps.symDir)
    )    

    return
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

def getMddlTaskContentList() {

    def mddlTaskContentList    = []

    mddlTaskList.each {

        def mddlFileName                = it.moduleName + '.' + it.moduleType
        def mddlPath                    = pipelineConfig.ispw.mddlRootFolder + '/' + ispwApplication + '/' + pipelineConfig.ispw.mddlFolder
        def mddlContent                 = readFile(file: mddlPath + '/' + mddlFileName)
        def records                     = mddlContent.split('\n')
        def mddlTaskContent             = getMddlTaskContent(records)   

        mddlTaskContent['taskId']       = it.taskId
        mddlTaskContent['moduleName']   = it.moduleName
        
        mddlTaskContentList.add(mddlTaskContent)
    
    }

    return mddlTaskContentList
}

def getMddlTaskContent(records) {

    def mddlTaskContent    = [:]

    records.each {

        if(it.charAt(0) != pipelineConfig.mddl.commentMarker) {

            def key     = it.split(pipelineConfig.mddl.valueMarker)[0].trim()
            def value   = it.split(pipelineConfig.mddl.valueMarker)[1].trim()
            
            if(pipelineConfig.mddl.keywords.contains(key)) {
                mddlTaskContent[key] = value
            }
            else {
                error "The MDDL Member contained unknown keyword: " + key
            }                
        }
    }

    return mddlTaskContent
}