pipeline {
    agent any
      environment {


    def String pipeline2Prefix = "D${Parm_ISPW_Application.substring(1, 4)}"
    def String pipeline1Prefix = "T${Parm_ISPW_Application.substring(1, 4)}"

    def String JobID = "${Parm_ISPW_Application}${BUILD_NUMBER}"
    def String Package_Version = '%'
    def String SSID = 'DCGC'
    def String DB2SSID = 'DSC2'
    def String Package_Name = 'CWXTCOB'
    def String Collection_ID = "${Parm_ISPW_Application}${Parm_ISPW_Level.substring(0, 3)}"
//    def String Parm_ISPW_Stg_level = "QA${Parm_ISPW_Level.substring(3, 1)}"   
    def String Parm_ISPW_Stg_level = "QA1"  
    def String Jenkins_Id          = "${Parm_ISPW_Owner}"    
  }

    stages {
        stage(Initialize) {
            steps {
            echo "Parm_ISPW_Owner = ${Parm_ISPW_Owner}"
            echo "Parm_ISPW_Application = ${Parm_ISPW_Application}"
            echo "Parm_ISPW_Stream = ${Parm_ISPW_Stream}"
            echo "Parm_ISPW_Level = ${Parm_ISPW_Level}"
            echo "Parm_ISPW_Assignment = ${Parm_ISPW_Assignment}"
            echo "pipeline2Prefix = ${pipeline2Prefix}"
            echo "pipeline1Prefix = ${pipeline1Prefix}"
            echo "Parm_ISPW_Stg_level = ${Parm_ISPW_Stg_level}"
            echo "JobID = ${JobID}"
            echo "Package_Version = ${Package_Version}"
            echo "SSID = ${SSID}"
            echo "DB2SSID = ${DB2SSID}"
            echo "Package_Name = ${Package_Name}"
            echo "Collection_ID = ${Collection_ID}"
            echo "JobID = ${JobID}"
            echo "Jenkins_Id = ${Jenkins_Id}"
 

            deleteDir() /* clean up our workspace */
 //            checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'ae938240-d336-42f2-9679-54bfdb668676', url: 'https://github.com/neilg001abc/BMCPipeline.git']]])
 
                checkout([$class: 'GitSCM', branches: [[name: '*/main']], extensions: [], userRemoteConfigs: [[credentialsId: 'Neils Github ID', url: 'https://github.com/neilg001abc/BMCPipeline.git']]])
            }                
        }


        stage('AMI Compare') {
            steps {
                echo 'Stage3 - AMI Compare'
                bmcAmiAuthentication comtype: 'ZOSMF', debug: false, dpassrun: '', dpassword: 'o++RSXEtJDlcDrfc2NlHoi4cajKVu37p 93,#-20,#122,#2dLqIXA==', dport: '443', dserver: 'cwc2.nasa.cpwr.corp', duser: 'HAUNXG0', pwdruntime: false, symdir: 'AMI_DevOps_CWC2.properties'
                bmcAmiJclExecution acceptableRC: '0004', debug: false, disablebuildstep: false, execpds: true, jdirectory: 'HAUNXG0.DEMO.JCL', jfilename: 'VERIFY', jobWaitTime: 2
                bmcAmiJclExecution acceptableRC: '0004', debug: false, disablebuildstep: false, execpds: true, jdirectory: 'HAUNXG0.DEMO.JCL', jfilename: 'DYNSQL', jobWaitTime: 2
//*               bmcAmiJclExecution acceptableRC: '0000', debug: false, disablebuildstep: false, execpds: true, jdirectory: 'HAUNXG0.DEV.CNTL', jfilename: 'BMCTEST', jobWaitTime: 2
bmcAmiDb2SchemaChangeMigration acceptableRC: '0004', analysisin: '''//*Analysis Input
//ANALYSIS.ALURPT DD DISP=SHR,
// DSN=&IMPRPT
//ANALYSIS.ALUIN DD *
  SSID  ${SSID}
  WORKID  ${Work ID Owner}.${Work ID Name}
  INCLUDE (DATA AMS SQL )
  IBMUNLOAD
  IBMLOAD
  IBMREORG''', cdlRollCheck: true, cdlRollPds: "#rbcdl#(${JobID})", cdlpds: "#cdlpds#(${JobID})", cmpbl1: '', cmpbl2: '', cmpbp1: '', cmpbp2: '', cmpddl1: '', cmpddl2: '', compin: '''//*Compare Input
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
     NODEFINE''', crule: '', crule1: '', crule2: '', cruleAfter: '', cruleBefore: '', debug: false, disablebuildstep: false, execjclpds: "#execpds#(${JobID})", genjcl: false, impin: '''//*Import Input
//IMPORT.ALUIN DD *
  SSID ${SSID}
  REPLACEWORKID  ${Work ID Owner}.${Work ID Name}
  SOURCETYPE CDL''', imprptpds: "#irpds#(${JobID})", jclgenin: '''//*JCLGEN Input
//STEP1.AJXIN DD *
  PRODUCT  ACM
  WORKID   ${Work ID Owner}.${Work ID Name}
  SSID     ${SSID}
  PREBASELINE Y
  PREBDIAG SYSOUT
  PREBPROFILE ${Pre-Baseline Profile Name}
  PREBNAME ${Pre-Baseline Name}
  POSTBASELINE Y
  POSTBDIAG SYSOUT
  POSTBPROFILE ${Post-Baseline Profile Name}
  POSTBNAME ${Post-Baseline Name}
  POSTCOMPARE G
  POSTCCDL ${Rollback PDS and Member Name (CDL)}
  POSTCDIAG SYSOUT''', jcomp: '''${Job Card}
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
${JCL Generation Input Stream}''', jobCardIn: '''//AMICMP1 JOB (#acctno#),\'COMPARE-${JobID}\',
//  CLASS=A,MSGLEVEL=(1,1)                
//*                                       
//*                                       
//*''', jobWaitTime: 2, location2: '', moduletype: 'compare3', nocdl: false, objPart1C1: 'FTS4QA', objPart1C2: 'FTS4DEV', objPart2C1: 'EMP', objPart2C2: 'EMP', objPart3C1: '', objPart3C2: '', objtyp: 'TB', postbaseexec: true, postbasename: "${JobID}_POST", postbaseprof: "${Parm_ISPW_Owner}.DEVOPS", preBaseType: 'preexec', prebasename: "${JobID}_PRE", prebaseprof: "${Parm_ISPW_Owner}.DEVOPS", ssid: "${DB2SSID}", useCrule: false, useCruleAfter: false, useCruleBefore: false, wkidname: "${JobID}", wkidowner: 'DEVOPS', wlistpds: "#wlpds#(${JobID})"

            }
        }
        stage('DBA Approval-Schema Chg') {
            steps {
                echo 'Stage2 - Pause'   
                bmcAmiDb2OutputTransmission debug: false, destFileName: "${JobID}",dfolder: "$WORKSPACE",disablebuildstep: false, localFileName: "${JobID}", sfolderImprpt: '#irpds#', sfoldercdl: '#cdlpds#', sfolderexec: '#execpds#', sfolderwlist: '#wlpds#'
                emailext attachmentsPattern: '${Job_ID}*.txt',
                body: '''Please review the changes requested by the developer related to the schema definition of the application.

See attached Impact Report and CDL files. ${job} <- 
JOb id -> ${JobID} <-

Use below link to look at the Compare report for CDL generated at AMI Command Centre for Db2

http://cwc2.nasa.cpwr.corp:3683/commandcenter/index.html?perspective=SchemaManager&cdl=HAUNXG0.AMI.DEVOPS.CDL(${JobID}) 

You can approve or reject implementation of changes  by using this link:

${BUILD_URL}/console''',
                subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Approve Change Request',
                to: 'ngilford@bmc.com' 
//       input "DBA Approval for Db2 Schema Change?"
        }
        }
        stage('Deploy DB2 Schema Change') {
            steps {
            echo 'Stage4 - Deploy DB2 Schema Change'
            bmcAmiJclExecution acceptableRC: '0004', debug: false, disablebuildstep: false, execpds: true, jdirectory: '#execpds#', jfilename: "${JobID}", jobWaitTime: 2
                }
        }
                stage('Generate Rollback') {
            steps {
            echo 'Stage5 - Generate Rollback'

bmcAmiDb2SchemaChangeMigration acceptableRC: '0004', analysisin: '''//*Analysis Input
//ANALYSIS.ALURPT DD DISP=SHR,
// DSN=&IMPRPT
//ANALYSIS.ALUIN DD *
  SSID  ${SSID}
  WORKID  ${Work ID Owner}.${Work ID Name}
  INCLUDE (DATA AMS SQL )
  IBMUNLOAD
  IBMLOAD
  IBMREORG''', cdlRollCheck: false, cdlRollPds: '', cdlpds: "#rbcdl#(${JobID})", cmpbl1: '', cmpbl2: '', cmpbp1: '', cmpbp2: '', cmpddl1: '', cmpddl2: '', compin: '', crule: '', crule1: '', crule2: '', cruleAfter: '', cruleBefore: '', debug: false, disablebuildstep: false, execjclpds: "#rbexec#(${JobID})", genjcl: false, impin: '''//*Import Input
//IMPORT.ALUIN DD *
  SSID ${SSID}
  REPLACEWORKID  ${Work ID Owner}.${Work ID Name}
  SOURCETYPE CDL''', imprptpds: "#rbir#(${JobID})", jclgenin: '''//*JCLGEN Input
//STEP1.AJXIN DD *
  PRODUCT  ACM
  WORKID   ${Work ID Owner}.${Work ID Name}
  SSID     ${SSID}''', jcomp: '''${Job Card}
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
${JCL Generation Input Stream}''', jobCardIn: '''//AMICMP2 JOB (#acctno#),\'COMPARE-${JobID}\',
//  CLASS=A,MSGLEVEL=(1,1)                
//*                                       
//*                                       
//*''', jobWaitTime: 2, location2: '', moduletype: 'Select comparison type', nocdl: true, objPart1C1: '', objPart1C2: '', objPart2C1: '', objPart2C2: '', objPart3C1: '', objPart3C2: '', objtyp: 'Select object type', postbaseexec: false, postbasename: '', postbaseprof: '', preBaseType: 'none', prebasename: '', prebaseprof: '', ssid: "${DB2SSID}", useCrule: false, useCruleAfter: false, useCruleBefore: false, wkidname: "${JobID}", wkidowner: 'DEVOPS', wlistpds: "#rbwl#(${JobID})"
                }
        }    
		stage('AMI SQL Assurance - Analyze Static SQL') {
		      steps {
		/* BMC AMI DevOps SQL Assurance for Db2 - Analyze Static SQL */
bmcAmiSqlAssuranceStaticSql collectionId: "${Collection_ID}", continueOnErr: false, debug: false, disableBuildStep: false, expMethod: 'Dynamic Explain', failWarnMode: 'Fail', genJcl: false, jcl: '''${Job Card}
//*
//        JCLLIB ORDER=(#proclib#)
//*
//JOBLIB  DD  DSN=#joblib1#,DISP=SHR
//        DD  DSN=#joblib2#,DISP=SHR
//        DD  DSN=#dsnexit#,DISP=SHR
//        DD  DSN=#dsnload#,DISP=SHR
//*
// SET RTEHLQ=#rtehlq#
// SET USRHLQ=#usrhlq#
//*
// EXPORT SYMLIST=(*)
//*
// SET SSID=${SSID}
// SET PSSPLAN=#pssplan#
// SET GUDPLAN=#gudplan#
// SET GENDEBUG=\'DUMMY\'
// SET LOGMODE=\'WARN\'
// SET RULSPACE=\'SPACE=(CYL,(10,10)),\'
// SET VTIBSZ=20
//*-----------------------------------------------------------------
//*
//SQLXPLOR EXEC COBPRPSS
${SQL Explorer Input Stream}
//RULESOUT.SYSUT2 DD &GENDEBUG
//*-----------------------------------------------------------------
//*
//IFPSS    IF (SQLXPLOR.PSSMAIN.RC EQ 4) THEN
//COBLOGER EXEC COBPRLOG
//LOGGER.AS$INPUT DD DSN=&&RULESOUT,
//            DISP=(OLD,DELETE)
//IFPSSEND ENDIF
//*''', jobCardIn: '''//COBSSQL JOB (#acctno#),\'STATIC-SQL\',
//  CLASS=A,MSGLEVEL=(1,1)
//*
//*
//*''', jobWaitTime: '3', listFilePath: '', objType: 'package', objectName: "${Package_Name}", sqlExpInParm: '''//PSSMAIN.SYSIN DD *
--NEWOBJ
OBJECT=PACKAGE
NAME="${Object Name}"
COLLID="${Collection ID (Package only)}"
VERSION="${Version (Package only)}"
RULES=#rules#
RULEDSN="#ruledsn#"
PLANTBL=N
CURRENCY=#currency#
MSGLEVEL=ALL
--ENDOBJ''', ssid: "${SSID}", useListFile: false, version: "${Package_Version}"
		      }
		}

        stage('DBA Approval-SQL Check') {
            steps {
                echo 'Stage4 - DBA Approval-SQL Chec'  
                	emailext attachmentsPattern: '*$BUILD_NUMBER-*Report',
  body: '''*************************** BMC AMI - SQL Assurance for Db2 *******************************
  
Jenkins Project: $JOB_NAME
Build#: $BUILD_NUMBER

Click below link to view the SQL Assurance Report on AMI Command Centre for Db2.
http://cwc2.nasa.cpwr.corp:3683/commandcenter/index.html

Use this URL to Approve/Reject the change on console output at "${BUILD_URL}/console" 
         ''', subject: 'SQL Check - Notification:$JOB_NAME - $BUILD_NUMBER', 
              to: 'ngilford@bmc.com' 
       input "DBA Approval after SQL Check?" 
        }
        } 
                stage('Extract Test Cases from Git') {
            steps {
                    echo "Stage5 - Build Tests"
                    echo "Stage5 - Checkout Source"

                checkout([$class: 'IspwContainerConfiguration', componentType: '', connectionId: 'afdca8c7-93e9-4fed-8a9f-30bddfa8d11b', containerName: "${Parm_ISPW_Assignment}", containerType: '0', credentialsId: 'HAUNXG0', ispwDownloadAll: true, ispwDownloadIncl: true, serverConfig: '', serverLevel: "${Parm_ISPW_Stg_Level}", targetFolder: 'source'])             
// copies tests to tests folder
            checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'Testing']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'Neils Github ID', url: 'https://github.com/neilg001abc/CWXTCOBb']]])                
            }
        }  
                stage('Execute Tests') {
            steps {
                    echo "Stage6 - Execute Tests"
// execute tests

// below CodeCoverage specified
//         totaltest clearCodeCoverage: true, 
//                       collectCCRepository: 'HAUNXG0.CODE.COVERAGE', 
//              collectCCSystem: 'CWC2TEST', 
//              collectCCTestID: 'TESTCWC2', 
//              collectCodeCoverage: true, 
//              connectionId: 'afdca8c7-93e9-4fed-8a9f-30bddfa8d11b', 
//              createJUnitReport: false, credentialsId: 'HAUNXG0', 
//              environmentId: 'CWC2-ISPW-GIT', 
//              folderPath: 'tests/BMC-CompuwareDemo/Tests/Scenarios', 
//              selectEnvironmentRadio: '-hci',
//              selectProgramsOption: true, jsonFile: 'changedPrograms.json',   /* to specify to test only list of pgms */
//              logLevel: 'ALL', reportFolder: 'tests/CWXTCOB_VT/Tests/Scenarios/Output', 
//              serverCredentialsId: 'HAUNXG0', 
//              serverUrl: 'http://cwc2.nasa.cpwr.corp:2020', sonarVersion: '6', 
//              sourceFolder: 'source/FTS4/MF_Source',
//              uploadToServer: true


            totaltest( 
                collectCCRepository: 'HAUNXG0.CODE.COVERAGE', 
                collectCCSystem: 'CWC2TEST', 
                collectCCTestID: 'TESTCWC2', 
                collectCodeCoverage: true, 
            localConfig:                        false,              
            recursive:                          true, 
            selectProgramsOption:               false, 
            haltPipelineOnFailure:              false,                 
            stopIfTestFailsOrThresholdReached:  false,
            createJUnitReport:                  true, 
            createReport:                       true, 
            createResult:                       true, 
            createSonarReport:                  true,
                connectionId: 'TEST DRIVE', 
                credentialsId: 'HAUNXG0', 
                environmentId: 'CWC2-ISPW-GIT', 
                folderPath: 'Testing', 
                serverCredentialsId: 'HAUNXG0', 
                serverUrl: 'http://cwc2.nasa.cpwr.corp:2020',
                sourceFolder: 'source/FTS4/MF_Source'
                )
              }
        }  
                        stage('Extract Code Coverage') {
            steps {

                    echo "stage7 - copy code coverage repo to jenkins"

string sources="source\\${Parm_ISPW_Application}\\MF_Source";
        string ccproperties   = 'cc.sources=' + sources + '\rcc.repos=HAUNXG0.CODE.COVERAGE' + '\rcc.system=CWC2TEST' + '\rcc.test=TESTCWC2' + '\rcc.ddio.overrides='
        step([$class: 'CodeCoverageBuilder',
        analysisProperties: ccproperties,
            analysisPropertiesPath: '',
            connectionId: '38e854b0-f7d3-4a8f-bf31-2d8bfac3dbd4',
            credentialsId: Jenkins_Id])
            }
        } 

        stage('DBA Approval-Dynamic SQL Check') {
            steps {
                echo 'Stage4 - Pause'  
                bmcAmiDb2OutputTransmission debug: false, destFileName: "${JobID}",dfolder: "$WORKSPACE",disablebuildstep: false, localFileName: "${JobID}", sfolderImprpt: '#irpds#', sfoldercdl: '#cdlpds#', sfolderexec: '#execpds#', sfolderwlist: '#wlpds#'
                	emailext attachmentsPattern: '*$BUILD_NUMBER-*Report',
  body: '''*************************** BMC AMI - SQL Assurance for Db2 *******************************
  
Jenkins Project: $JOB_NAME
Build#: $BUILD_NUMBER

Click below link to view the SQL Assurance Report on AMI Command Centre for Db2.
http://cwc2.nasa.cpwr.corp:3683/commandcenter/index.html

Use this URL to Approve/Reject the change on console output at "${BUILD_URL}/console" 
         ''', subject: 'SQL Check - Notification:$JOB_NAME - $BUILD_NUMBER', 
              to: 'dkarunak@bmc.com' 
        }
        }  

stage('SonarQube Quality Gates') {
  steps {
    echo "stage8- sonarqube quality gates"
      withSonarQubeEnv("Sonar") {
        script {
          def SQ_Scanner_Name = "Scanner";
          def scannerHome = tool "${SQ_Scanner_Name}";
          def cobolTypes = "cbl,testsuite,testscenario,stub,result,scenario,context";
          def SQ_Scanner_Properties1 = " -Dsonar.log.level=INFO -Dsonar.scm.exclusions.disabled=true -Dsonar.tests=Testing/TestingCWXTCOBb/Tests  -Dsonar.projectKey=BMCPromote -Dsonar.projectName=BMCPromote -Dsonar.sources=source/${Parm_ISPW_Application}/MF_Source"

// use -X to switch debug on in beloe
         def SQ_Scanner_Properties2 = " -Dsonar.cobol.copy.directories=source/${Parm_ISPW_Application}/MF_Source -Dsonar.cobol.file.suffixes=${cobolTypes} -Dsonar.cobol.copy.suffixes=cpy -Dsonar.sourceEncoding=UTF-8 -Dsonar.testExecutionReportPaths=TTTSonar/Testing.cli.suite.sonar.xml -Dsonar.coverageReportPaths=Coverage/CodeCoverage.xml"
         echo SQ_Scanner_Properties1
         echo SQ_Scanner_Properties2

         bat "${scannerHome}/bin/sonar-scanner ${SQ_Scanner_Properties1}" + SQ_Scanner_Properties2
        }
          
      }

     }
  }
  stage("Check Quality Gate")
{
    steps { 
        script {
        timeout(time: 2, unit: 'MINUTES') {
            // Wait for webhook call back from SonarQube
            def qg = waitForQualityGate()
            
            // Evaluate the status of the Quality Gate
            if (qg.status != 'OK')
            {
                try {
                    echo "Pipeline aborted due to quality gate failure: ${qg.status}"
                    error "Exiting Pipeline" // Exit the pipeline with an error if the SonarQube Quality Gate is failing
                }
                catch(e) {
                   //build_ok = false
                   echo e.toString() 
                }
            }   
            else{
                echo "Quality Gate status is: {${qg.status}"
            } }
        } 
    }
}
}
}