import groovy.json.JsonSlurper
import groovy.json.JsonOutput 

def tableName
def assignmentId
def configFile
def ispwLevel
def pipelineConfig
def mddlFileExtension
def xferFolder
def ftpTextSetup 
def ftpTextPut   
def ftpTextClose 
def ftpText      


def call(Map execParms) {

    tableName           = execParms.tableName
    assignmentId        = execParms.assignmentId
    configFile          = 'mddlPipeline.yml'
    mddlFileExtension   = 'mddl'
    ispwLevel           = 'UT'
    xferFolder          = 'xfer'
    targetLib           = '/u/hddrxm0/abn' //'SALESSUP.ABN1.UT.MDDL'
    targetPds           = 'SALESSUP.ABN1.UT.MDDL'

    ftpTextSetup        = ""
    ftpTextPut          = ""
    ftpTextClose        = "quit\r"
    ftpText             = ""

    node {

        stage("Initialize"){

            dir("./"){
                deleteDir()
            }

            initialize()

        }

        stage("Create MDDL Template") {

            createMddlFile()

            uploadMddlFile()
        
        }

        stage('Load Tasks') {

            loadTask()

        }
    }
}

def initialize() {
    dir("./") 
    {
        deleteDir()
    }

    pipelineConfig      = readYaml(text: libraryResource(configFile))
    cesUrl              = pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port
}

def createMddlFile() {
    def mddlFileName    = xferFolder + "/" + tableName + "." + mddlFileExtension
    
    def mddlContent = [:]
    mddlContent.mddl = pipelineConfig.mddlTemplate

    mddlContent.mddl.source.table = tableName
    mddlContent.mddl.target.table = tableName

    writeYaml(
        file:   mddlFileName,
        data:   mddlContent
    )
    
    def tmpText = readFile(
        file: mddlFileName
    )

    tmpText.replace('\n', '\n\r')

    writeFile(
        file: mddlFileName,
        text: tmpText
    )
}

def uploadMddlFile() {

    def listOfXferFiles = []

    withCredentials(
        [
            usernamePassword(
                credentialsId:      pipelineConfig.host.credentialsId, 
                passwordVariable:   'pwTmp', 
                usernameVariable:   'userTmp'
            )
        ]
    )
    {

    ftpTextSetup = """
dummy
open ${pipelineConfig.host.name}
${userTmp}
${pwTmp}
quote site sbsendeol=crlf
lcd ${xferFolder}
cd '${targetPds}'
ascii
hash
"""


//hash
    }

    echo "Search for:"
    echo "${xferFolder}/**/*.${mddlFileExtension}"

    def listOfXferFilesPaths = findFiles(glob: "${xferFolder}/**.${mddlFileExtension}")

//    echo "Found"
//    echo listOfXferFilesPaths.toString()

    listOfXferFilesPaths.each
    {
        def fileNameFull    = it.name            
        def fileNameBase    = fileNameFull.substring(0, fileNameFull.indexOf(".${mddlFileExtension}"))
        ftpTextPut          = ftpTextPut + "put ${fileNameBase}.${mddlFileExtension} ${fileNameBase}\r"

        echo "Adding File " + fileNameFull

        listOfXferFiles.add(fileNameFull)
    }

    ftpText = ftpTextSetup + ftpTextPut + ftpTextClose

    writeFile(file: 'xfer.txt', text: ftpText)
    def stdout = bat(returnStdout: true, script: 'ftp -i -s:xfer.txt')
    echo stdout

//     topazSubmitFreeFormJcl(
//         connectionId:   pipelineConfig.host.connectionId, 
//         credentialsId:  pipelineConfig.host.credentialsId, 
//         jcl: """
// //HDDRXM0X JOB ('EUDD,INTL'),'NUESSE',NOTIFY=&SYSUID,
// //             MSGLEVEL=(1,1),MSGCLASS=X,CLASS=A,REGION=6M
// /*JOBPARM S=*
// //COPYFILE EXEC PGM=IKJEFT01
// //IN DD PATH=\'${targetLib}/${tableName}\'
// //OUT DD DISP=SHR,DSN=${targetPds}(${tableName})
// //SYSTSPRT DD SYSOUT=*
// //SYSTSIN DD *
// OCOPY INDD(IN) OUTDD(OUT) TEXT
// /*
// """,
//        maxConditionCode: '4'
//     )
}

def loadTask() {

    def response = ispwOperation(
        connectionId:           pipelineConfig.host.connectionId, 
        consoleLogResponseBody: true, 
        credentialsId:          pipelineConfig.ces.credentialsId, 
        ispwAction:             'TaskLoad', 
        ispwRequestBody: '''
            runtimeConfiguration=''' + pipelineConfig.ispw.runtimeConfig + '''
            assignmentId=''' + assignmentId + '''
            stream=ABN
            application=ABN1
            currentLevel=UT
            startingLevel=UT
            moduleName=''' + tableName + '''
            moduleType=MDDL
        '''
    )

}

    // stage('Create Assignment') {

    //     def response = ispwOperation(
    //         connectionId:           hostConnection, 
    //         consoleLogResponseBody: true, 
    //         credentialsId:          cesCredentials, 
    //         ispwAction:             'CreateAssignment', 
    //         ispwRequestBody: '''
    //             runtimeConfiguration=''' + runtimeConfig + '''
    //             stream=''' + stream + '''
    //             application=''' + application + '''
    //             defaultPath=''' + targetPath + '''
    //             description=''' + assignmentDescription + '''
    //             assignmentPrefix=''' + application + '''
    //         '''     
    //     )

    //     assignmentId = new JsonSlurper().parseText(response.getContent()).assignmentId
    //     echo "Assignment ID: " + assignmentId

    // }

    // }    

    // stage('Deploy') {
        
    //     def response = ispwOperation(
    //         connectionId:           hostConnection, 
    //         consoleLogResponseBody: true, 
    //         credentialsId:          cesCredentials, 
    //         ispwAction:             'DeployAssignment', 
    //         ispwRequestBody: '''
    //             runtimeConfiguration=''' + runtimeConfig + '''
    //             assignmentId=''' + assignmentId + '''
    //             level=''' + targetLevel + '''
    //         '''
    //     )

    // }

    // stage('Get Assignment Info'){

    //     def response = ispwOperation(
    //         connectionId:   hostConnection, 
    //         credentialsId:  cesCredentials, 
    //         ispwAction: 'GetAssignmentTaskList', 
    //         ispwRequestBody: '''
    //             runtimeConfiguration=''' + runtimeConfig + '''            
    //             assignmentId=''' + assignmentId + '''
    //         '''
    //     )
        
    //     def assignmentInfo          = new JsonSlurper().parseText(response.getContent())
    //     def assignmentInfoJson      = new JsonOutput().toJson(assignmentInfo)
    //     def assignmentInfoText      = assignmentInfoJson.toString()
        
    //     echo assignmentInfoText
        
    //     def promoteInfo             = [:]
    //     promoteInfo.assignmentId    = assignmentId
    //     promoteInfo.currentLevel    = assignmentInfo.tasks[0].level
    //     def promoteInfoJson         = new JsonOutput().toJson(promoteInfo)
    //     def promoteInfoText         = promoteInfoJson.toString()

    //     echo promoteInfoText

    //     assignmentInfo              = null
    //     assignmentInfoJson          = null
    //     promoteInfoJson             = null

    //     writeFile(file: assignmentInfoFile, text: assignmentInfoText)
    //     writeFile(file: promoteInfoFile, text: promoteInfoText)
    // }

    // stage("Generate") {
    //     ispwOperation(
    //         connectionId:   hostConnection, 
    //         credentialsId:  cesCredentials, 
    //         ispwAction:     'GenerateTasksInAssignment', 
    //         ispwRequestBody: '''
    //             runtimeConfiguration=''' + runtimeConfig + '''
    //             assignmentId=''' + assignmentId + '''
    //             level=''' + targetLevel + '''
    //         '''
    //     )
    // }