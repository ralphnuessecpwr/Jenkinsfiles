//import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
//import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
//import hudson.model.Result;

def execParms
def pipelineConfig
def mddlTaskList
def ispwCurrentLevel
def db2SourceLevel
def db2TargetLevel
def cesUrl
def symdir
def mddlTaskContentList
def mddlTaskContent
def analysisIn
def compIn
def importIn
def jclGenIn
def compareJcl
def jobcard
def workIdOwner
def workIdName

def call(eParms, pConfig, mTaskList, currentLevel, sourceLevel, targetLevel, cesUrl) {

    initialize(eParms, pConfig, mTaskList, currentLevel, sourceLevel, targetLevel, cesUrl)

    stage("Retrieve MDDL members") {

        downloadMddlMembers()

    }

    stage("Process MDDL members") {

        mddlTaskContentList = getMddlTaskContentList()

        echo "Task Content List"
        echo mddlTaskContentList.toString()

        mddlTaskContent = mddlTaskContentList[0]
        workIdOwner     = mddlTaskContent.userId
        workIdName      = mddlTaskContent.moduleName + "S"
    }

    stage("Schema Implement") {

        echo "Implementing schema at user level"
        runAuthentication()

        implementSchema()
    }
}

def initialize(eParms, pConfig, mTaskList, currentLevel, sourceLevel, targetLevel, cesUrl) {

    dir("./") 
    {
        deleteDir()
    }

    execParms           = eParms
    pipelineConfig      = pConfig
    mddlTaskList        = mTaskList
    ispwCurrentLevel    = currentLevel
    db2SourceLevel      = sourceLevel
    db2TargetLevel      = targetLevel
    cesUrl              = cesUrl

    createAmiDevOpsProperties()

    analysisIn          = libraryResource(pipelineConfig.amiDevOps.analysisIn)
    compIn              = libraryResource(pipelineConfig.amiDevOps.compIn)
    importIn            = libraryResource(pipelineConfig.amiDevOps.importIn)
    jclGenIn            = libraryResource(pipelineConfig.amiDevOps.jclGenIn)
    compareJcl          = libraryResource(pipelineConfig.amiDevOps.jcl)
    jobcard             = libraryResource(pipelineConfig.amiDevOps.jobcard)

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
            containerName:      ispwReleaseId, 
            containerType:      pipelineConfig.ispw.containerTypeRelease, 
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

def runAuthentication() {

    withCredentials(
        [   
            usernamePassword(
                credentialsId: pipelineConfig.host.credentialsId, 
                usernameVariable: 'hostUser',
                passwordVariable: 'hostPassword'
            )
        ]
    ) {

        echo "Authorizing user " + hostUser

        bmcAmiAuthentication(
            comtype:    'ZOSMF', 
            dserver:    pipelineConfig.host.name, 
            dport:      pipelineConfig.host.zosmfPort,                     
            duser:      hostUser, 
            pwdruntime: false,                    
            dpassrun:   '', 
            dpassword:  pipelineConfig.amiDevOps.credentialsId, //'Z2DS0c1t6aThswpuhBtme3A67nX2/AhE 64,#-124,#67,#-alW4UQ==',
            debug:      false, 
            symdir:     pipelineConfig.amiDevOps.symDir
        )
    }

    return
}

def implementSchema() {

    bmcAmiJclExecution(
        acceptableRC: '0000', 
        debug: false, 
        disablebuildstep: false, 
        execpds: true, 
        jdirectory: "HDDRXM0.AMI.DEVOPS.ST.EXECJCL", 
        jfilename: workIdName, 
        jobWaitTime: 2
    )
}
