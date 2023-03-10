import groovy.json.JsonSlurper
import groovy.json.JsonOutput 

def tableName       = 'KTDEMO'
def assignmentId    = ''
def configFile      = 'mddlPipeline.yml'
def ispwLevel       = 'UT'
def pipelineConfig

node {

    stage("Initialize"){

        dir("./"){
            deleteDir()
        }

        initialize()

    }

    stage("Create MDDL Template") {

        createMddlFile()

        // uploadMddlFile()

        // loadTadk()
    
    }
}

def createMddlFile() {
    def fileName    = tableName + '.mddl'
    def mddlContent = [:]
    mddlContent.mddl = pipelineConfig.mddlTemplate

    echo .toString()
}

def uploadMddlFile() {

    withCredentials(
        [
            usernamePassword(
                credentialsId:      hostCredentials, 
                passwordVariable:   'pwTmp', 
                usernameVariable:   'userTmp'
            )
        ]
    )
    {
/*
            ftpTextSetup = """
open ${hostName}
${userTmp}
${pwTmp}
lcd ${xferFolder}
cd '${targetLib}'
ascii
hash
"""
*/
    }

    echo "Search for:"
    echo "${xferFolder}/**/*.${fileExtension}"

    def listOfXferFilesPaths = findFiles(glob: "${xferFolder}/**/*.${fileExtension}")

    echo "Found"
    echo listOfXferFilesPaths.toString()

    listOfXferFilesPaths.each
    {
        def fileNameFull    = it.name            
        def fileNameBase    = fileNameFull.substring(0, fileNameFull.indexOf(".${fileExtension}"))
//            ftpTextPut          = ftpTextPut + "put ${fileNameBase}.${fileExtension} ${fileNameBase}\n"

        echo "Adding File " + fileNameFull

        listOfXferFiles.add(fileNameFull)
    }

//        ftpText = ftpTextSetup + ftpTextPut + ftpTextClose

//        writeFile(file: 'xfer.txt', text: ftpText)
//        def stdout = bat(returnStdout: true, script: 'ftp -i -s:xfer.txt')
//        echo stdout
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

    // stage('Load Tasks') {

    //     def listOfTaskInfos = []

    //     listOfXferFiles.each{

    //         def taskInfo        = [:]
    //         def sourceMem       = it.substring(0, it.indexOf(".${fileExtension}"))
    //         taskInfo.memberName = sourceMem

    //         def response = ispwOperation(
    //             connectionId:           hostConnection, 
    //             consoleLogResponseBody: true, 
    //             credentialsId:          cesCredentials, 
    //             ispwAction:             'TaskLoad', 
    //             ispwRequestBody: '''
    //                 runtimeConfiguration=''' + runtimeConfig + '''
    //                 assignmentId=''' + assignmentId + '''
    //                 stream=''' + stream + '''
    //                 application=''' + application + '''
    //                 currentLevel=''' + targetLevel + '''
    //                 startingLevel=''' + targetPath + '''
    //                 moduleName=''' + sourceMem + '''
    //                 moduleType=''' + sourceType + '''
    //             '''
    //         )

    //     }
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
}

def initialize() {

    dir("./") 
    {
        deleteDir()
    }

    pipelineConfig      = readYaml(text: libraryResource(configFile))
    cesUrl              = pipelineConfig.ces.hostName + ':' + pipelineConfig.ces.port
}
