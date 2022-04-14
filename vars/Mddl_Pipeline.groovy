//import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
//import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
//import hudson.model.Result;

def execParms
def pipelineConfig
def mddlTaskList
def ispwCurrentLevel
def cesUrl
def symdir
def mddlTaskContentList
def analysisIn
def compIn
def importIn
def jclGenIn
def compareJcl
def jobcard

def call(eParms, pConfig, mTaskList, iCurrentLevel, cUrl) {

    initialize(eParms, pConfig, mTaskList, iCurrentLevel, cUrl)

    stage("Retrieve MDDL members") {

        downloadMddlMembers()

    }

    stage("Process MDDL members") {

        mddlTaskContentList = getMddlTaskContentList()

    }

    stage('Compare Developer Schema definition against Production') {

        def mddlTaskContent = mddlTaskContentList[0]
        def workIdOwner     = mddlTaskContent.userId
        def workIdName      = mddlTaskContent.moduleName
        def Job_ID           = BUILD_NUMBER

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
                pwdruntime: false,                    
                dpassrun:   '${hostPassword}', 
                dpassword:  '7pfDq2vJ6eEfXsQ3ZY1A6kXRKDxdDMs1 123,#-103,#25,#GFUXxg==',
                debug:      true, 
                symdir:     pipelineConfig.amiDevOps.symDir
            )
        }

        bmcAmiDb2SchemaChangeMigration(
            acceptableRC:   '0000', 
            jobWaitTime:    2, 
            moduletype:     'compare3', 
            nocdl:          false, 
            objtyp:         'TS', 
            location2:      mddlTaskContent.DB2PSSID,
            ssid:           mddlTaskContent.DB2SSID,
            objPart1C1:     mddlTaskContent.DB2DB, 
            objPart1C2:     mddlTaskContent.DB2PDB, 
            objPart2C1:     mddlTaskContent.DB2TS, 
            objPart2C2:     mddlTaskContent.DB2PTS, 
            objPart3C1:     '', 
            objPart3C2:     '', 
            postbaseexec:   false, 
            postbasename:   '', 
            postbaseprof:   '', 
            preBaseType:    'none', 
            prebasename:    '', 
            prebaseprof:    '', 
            useCrule:       false, 
            useCruleAfter:  false, 
            useCruleBefore: false, 
            wkidname:       workIdName, 
            wkidowner:      workIdOwner, 
            wlistpds:       'HDDRXM0.DEMO.JCL(AMIWL)',
            cdlRollCheck:   false, 
            cdlRollPds:     '', 
            cdlpds:         'HDDRXM0.DEMO.JCL(AMICDL)', 
            cmpbl1:         '', 
            cmpbl2:         '', 
            cmpbp1:         '', 
            cmpbp2:         '', 
            cmpddl1:        '', 
            cmpddl2:        '', 
            crule:          '', 
            crule1:         '', 
            crule2:         '', 
            cruleAfter:     '', 
            cruleBefore:    '', 
            debug:          false, 
            disablebuildstep: false, 
            execjclpds:     'HDDRXM0.DEMO.JCL(AMIEXEC)', 
            genjcl:         false, 
            imprptpds:      '',                 
            analysisin:     analysisIn, 
            compin:         compIn, 
            impin:          importIn, 
            jclgenin:       jclGenIn, 
            jcomp:          compareJcl, 
            jobCardIn:      jobCardIn
        )
    }
}

def initialize(eParms, pConfig, mTaskList, iCurrentLevel, cUrl) {

    dir("./") 
    {
        deleteDir()
    }

    execParms           = eParms
    pipelineConfig      = pConfig
    mddlTaskList        = mTaskList
    ispwCurrentLevel    = iCurrentLevel
    cesUrl              = cUrl

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
        def mddlContent                 = readFile(file: mddlPath + '/' + mddlFileName)
        def records                     = mddlContent.split('\n')
        def mddlTaskContent             = getMddlTaskContent(records)   

        mddlTaskContent['taskId']       = it.taskId
        mddlTaskContent['moduleName']   = it.moduleName
        mddlTaskContent['userId']       = it.userId
        
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