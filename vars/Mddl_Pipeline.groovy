//import static com.bmc.db2.bmcclient.BMCClientCN.bmcCN;
//import static com.bmc.db2.bmcclient.BMCClientSM.bmcSM;
//import hudson.model.Result;

def symdir
def mddlTaskList
def mddlTaskContentList

def call(execParms, pipelineConfig, mddlTaskList, ispwCurrentLevel, cesUrl) {

    node {

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
                acceptableRC: '0000', 
                jobWaitTime: 2, 
                moduletype: 'compare3', 
                nocdl: false, 
                objtyp: 'TS', 
                location2: mddlTaskContent.DB2PSSID,
                ssid: mddlTaskContent.DB2SSID,
                objPart1C1: mddlTaskContent.DB2DB, 
                objPart1C2: mddlTaskContent.DB2PDB, 
                objPart2C1: mddlTaskContent.DB2TS, 
                objPart2C2: mddlTaskContent.DB2PTS, 
                objPart3C1: '', 
                objPart3C2: '', 
                postbaseexec: false, 
                postbasename: '', 
                postbaseprof: '', 
                preBaseType: 'none', 
                prebasename: '', 
                prebaseprof: '', 
                useCrule: false, 
                useCruleAfter: false, 
                useCruleBefore: false, 
                wkidname: workIdName, 
                wkidowner: workIdOwner, 
                wlistpds: 'HDDRXM0.DEMO.JCL(AMIWL)',
                cdlRollCheck: false, 
                cdlRollPds: '', 
                cdlpds: 'HDDRXM0.DEMO.JCL(AMICDL)', 
                cmpbl1: '', 
                cmpbl2: '', 
                cmpbp1: '', 
                cmpbp2: '', 
                cmpddl1: '', 
                cmpddl2: '', 
                crule: '', 
                crule1: '', 
                crule2: '', 
                cruleAfter: '', 
                cruleBefore: '', 
                debug: false, 
                disablebuildstep: false, 
                execjclpds: 'HDDRXM0.DEMO.JCL(AMIEXEC)', 
                genjcl: false, 
                imprptpds: '',                 
                analysisin: '''
//*Analysis Input
//ANALYSIS.ALURPT DD DISP=SHR,
// DSN=&IMPRPT
//ANALYSIS.ALUIN DD *
  SSID  ${SSID}
  WORKID  ${Work ID Owner}.${Work ID Name}
  INCLUDE (DATA AMS SQL )''', 
                compin: '''//*Compare Input
//COMPARE1 EXEC AMAPROCC,
// COND=(1,LT)
//COMPARE.CDL001 DD DISP=SHR,
// DSN=&CDLFILE
//COMPARE.ALUIN DD *
     SSID  ${SSID}
     CMPTYPE1 LOCAL
     CMPTYPE2 LOCAL
     INCLUDE (ALTER CREATE DROP )
     REPORT (DETAIL )
     SCOPETYPE RULE
     SCOPERULE1 (* ${Object Type (applies to Compare1 and Compare2)} ${Object Name Part 1 for Compare1}.${Object Name Part 2 for Compare1})
     SCOPERULE2 (* ${Object Type (applies to Compare1 and Compare2)} ${Object Name Part 1 for Compare2}.${Object Name Part 2 for Compare2})
     NOSYNONYM
     NOALIAS
     NODEFINE''', 
                impin: '''//*Import Input
//IMPORT.ALUIN DD *
  SSID ${SSID}
  REPLACEWORKID  ${Work ID Owner}.${Work ID Name}
  SOURCETYPE CDL''', 
                jclgenin: '''//*JCLGEN Input
//STEP1.AJXIN DD *
  PRODUCT  ACM
  WORKID   ${Work ID Owner}.${Work ID Name}
  SSID     ${SSID}''', 
                jcomp: '''${Job Card}
//*
//        JCLLIB ORDER=(#proclib#)
//*
//JOBLIB  DD  DSN=#joblib1#,DISP=SHR
//        DD  DSN=#joblib2#,DISP=SHR
//        DD  DSN=#dsnexit#,DISP=SHR
//        DD  DSN=#dsnload#,DISP=SHR
//***************************************************
// SET ACMDOPT=#dopts#
// SET RTEHLQ=#rtehlq#
// SET USRHLQ=#usrhlq#
//***************************************************
// SET DDLFILE=${Compare2 DDL PDS and Member Name}
// SET CDLFILE=${CDL PDS and Member Name}
// SET WORKLIST=${Worklist PDS and Member Name}
// SET EXECJCL=${Execution JCL PDS and Member Name}
// SET IMPRPT=${Impact Report PDS and Member Name}
//*
// SET POFFILE=#pofdsn#
//*----------------------------------------------------
//* BMC CHANGE MANAGER COMPARE
//*----------------------------------------------------
${Compare Input Stream}
//*----------------------------------------------------
//* BMC CHANGE MANAGER IMPORT
//*----------------------------------------------------
//IMPORT   EXEC AMAPROCI,
//    COND=${BMC_COMPARE_COND_CODE}
//IMPORT.IMPORTIN DD DISP=SHR,
//  DSN=&CDLFILE
${Import Input Stream}
//*----------------------------------------------------
//* BMC CHANGE MANAGER ANALYSIS
//*----------------------------------------------------
//ANALYSIS EXEC AMAPROCA,
//    COND=${BMC_COMPARE_COND_CODE}
${Analysis Input Stream}
//*----------------------------------------------------
//* BMC JCL GENERATION
//*----------------------------------------------------
//JCLGEN   EXEC AMAPROCJ, 
//    COND=${BMC_COMPARE_COND_CODE}
${JCL Generation Input Stream}''', 
                jobCardIn: '''//HDDRXM0J JOB (#acctno#),\'COMPARE-${Job_ID}\',
//  CLASS=A,MSGLEVEL=(1,1)                
//*                                       
//*                                       
//*'''
            )
        }
    }
}

def initialize(execParms) {

    dir("./") 
    {
        deleteDir()
    }

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