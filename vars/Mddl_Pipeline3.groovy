//import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
//import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
//import hudson.model.Result;

def execParms
def pipelineConfig
def mddlTaskList
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

execParms, pipelineConfig, mddlTaskList, ispwSourceLevel, ispwTargetLevel, cesUrl

def call(eParms, pConfig, mTaskList, sourceLevel, targetLevel, cesUrl) {

    initialize(eParms, pConfig, mTaskList, sourceLevel, targetLevel, cesUrl)

    stage("Retrieve MDDL members") {

        downloadMddlMembers()

    }

    stage("Process MDDL members") {

        mddlTaskContentList = getMddlTaskContentList()

        echo "Task Content List"
        echo mddlTaskContentList.toString()

    }

    stage('Compare Developer Schema definition against Production') {

        mddlTaskContent = mddlTaskContentList[0]
        workIdOwner     = mddlTaskContent.userId
        workIdName      = mddlTaskContent.moduleName
        jobcard         = jobcard.replace('${Job_ID}', BUILD_NUMBER)

echo mddlTaskContent.mddl.source.database
echo mddlTaskContent.mddl.target.database
echo mddlTaskContent.mddl.source.tablespace
echo mddlTaskContent.mddl.target.tablespace
echo mddlTaskContent.mddl.source.table
echo mddlTaskContent.mddl.target.table

echo mddlTaskContent.mddl.source.tablespace
echo mddlTaskContent.mddl.target.tablespace


    //     runAuthentication(pipelineConfig)
        
    //     runComparison(workIdName)

    }

    // stage("Process Results"){

    //     bat ('mkdir ' + pipelineConfig.amiDevOps.outputFolder)

    //     bmcAmiDb2OutputTransmission(
    //         debug:              false, 
    //         destFileName:       workIdName, 
    //         dfolder:            './' + pipelineConfig.amiDevOps.outputFolder, 
    //         disablebuildstep:   false, 
    //         localFileName:      workIdName, 
    //         sfolderImprpt:      pipelineConfig.amiDevOps.datasetNames.work.importpds,
    //         sfoldercdl:         pipelineConfig.amiDevOps.datasetNames.work.cdlpds, 
    //         sfolderexec:        pipelineConfig.amiDevOps.datasetNames.work.execjclpds, 
    //         sfolderwlist:       pipelineConfig.amiDevOps.datasetNames.work.wlistpds
    //     )

    // }
}

def initialize(eParms, pConfig, mTaskList, sourceLevel, cesUrl) {

    dir("./") 
    {
        deleteDir()
    }

    execParms           = eParms
    pipelineConfig      = pConfig
    mddlTaskList        = mTaskList
    ispwCurrentLevel    = sourceLevel
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

def runAuthentication(pipelineConfig) {

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
            dpassrun:   '', 
            dpassword:  pipelineConfig.amiDevOps.credentialsId, //'Z2DS0c1t6aThswpuhBtme3A67nX2/AhE 64,#-124,#67,#-alW4UQ==',
            debug:      false, 
            symdir:     pipelineConfig.amiDevOps.symDir
        )
    }

    return
}

def runComparison(workIdName) {

        bmcAmiDb2SchemaChangeMigration(
            acceptableRC:   '0004', 
            jobWaitTime:    2, 
            moduletype:     'compare3', 
            nocdl:          false, 
            objtyp:         'TS', 
            ssid:           mddlTaskContent.mddl.source.ssid,
            objPart1C2:     mddlTaskContent.mddl.target.database, 
            objPart3C1:     '', 
            location2:      mddlTaskContent.mddl.target.ssid,
            objPart1C1:     mddlTaskContent.mddl.source.database, 
            objPart2C1:     mddlTaskContent.mddl.source.tablespace, 
            objPart2C2:     mddlTaskContent.mddl.target.tablespace, 
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
            wkidowner:      workIdOwner, 
            wkidname:       workIdName,             
            wlistpds:       "#wlpds#(${workIdName})",
            cdlRollCheck:   false, 
            cdlRollPds:     '', 
            cdlpds:         "#cdlpds#(${workIdName})",
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
            execjclpds:     "#execpds#(${workIdName})",
            genjcl:         false, 
            imprptpds:      "#irpds#(${workIdName})",                 
            analysisin:     analysisIn, 
            compin:         compIn, 
            impin:          importIn, 
            jclgenin:       jclGenIn, 
            jcomp:          compareJcl, 
            jobCardIn:      jobcard
        )
    
    return
}