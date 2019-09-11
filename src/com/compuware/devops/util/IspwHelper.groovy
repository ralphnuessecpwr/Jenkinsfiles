package com.compuware.devops.util

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.*
import com.compuware.devops.jclskeleton.*

/* Wrapper class to simplify use of ISPW functions */
class IspwHelper implements Serializable 
{
    def steps

    String ispwUrl
    String ispwRuntime
    String ispwStream
    String ispwApplication
    String ispwRelease
    String ispwAssignment
    String ispwSetId
    String applicationPathNum
    String ispwOwner
    String ispwTargetLevel
    String mfSourceFolder
    String hciConnId
    String hciTokenId

    IspwHelper(steps, pConfig) 
    {
        this.steps              = steps
        this.ispwUrl            = pConfig.ispwUrl
        this.ispwRuntime        = pConfig.ispwRuntime
        this.ispwStream         = pConfig.ispwStream
        this.ispwApplication    = pConfig.ispwApplication
        this.ispwRelease        = pConfig.ispwRelease      
        this.ispwAssignment     = pConfig.ispwAssignment  
        this.ispwSetId          = pConfig.ispwSetId
        this.ispwOwner          = pConfig.ispwOwner
        this.ispwTargetLevel    = pConfig.ispwTargetLevel
        this.applicationPathNum = pConfig.applicationPathNum

        this.mfSourceFolder     = pConfig.mfSourceFolder

        this.hciConnId          = pConfig.hciConnId
        this.hciTokenId         = pConfig.hciTokenId
    }

    /* Download all sources from ISPW for a given level */
    def downloadAllSources(String level)
    {
        steps.checkout( 
            changelog: false, 
            poll: false, 
            scm: [
                $class: 'IspwConfiguration', 
                    componentType: 'COB, COPY', 
                    connectionId: "${hciConnId}", 
                    credentialsId: "${hciTokenId}",      
                    folderName: '', 
                    ispwDownloadAll: true, 
                    levelOption: '0', 
                    serverApplication: "${ispwApplication}",
                    serverConfig: "${ispwRuntime}", 
                    serverLevel: "${level}", 
                    serverStream: "${ispwStream}"
                ]
        )
    }

    /* Download sources for a container and level */
    private downloadSources(String level, String container, String containerType)
    {
        steps.checkout([
            $class:             'IspwContainerConfiguration', 
            componentType:      '',                         // optional filter for component types in ISPW
            connectionId:       "${hciConnId}",     
            credentialsId:      "${hciTokenId}",      
            containerName:      "${container}",   
            containerType:      "${containerType}",     // 0-Assignment 1-Release 2-Set
            ispwDownloadAll:    true,                       // false will not download files that exist in the workspace and haven't previous changed
            serverConfig:       '',                         // ISPW runtime config.  if blank ISPW will use the default runtime config
            serverLevel:        "${level}"              // level to download the components from
        ])
    }

    /* Download sources for the ISPW Set which triggered the current pipeline from a given level */
    def downloadSourcesForSet(String ispwLevel)
    {
        downloadSources(ispwLevel, ispwSetId, '2')
    }

    /* Download sources for the ISPW assignment from a given level */
    def downloadSourcesForAssignment(String ispwLevel)
    {
        downloadSources(ispwLevel, ispwAssignment, '0')
    }

    /* Download copy books used in the downloaded sources  */
    /* Since copy books do not have to be part of the current set, the downloaded programs need to be parsed to determine copy books */
    /* Since the SCM downloader plugin does not provide the option to download specific members, */
    /* the required copy books will be copied from the ISPW libraries to a single PDS using an IEBCOPY job */
    /* Then this PDS will be downloaded */
    def downloadCopyBooks(String workspace)
    {
        /* Class JclSkeleton will allow using "JCL Skeletons" to generate the requires JCL */
        JclSkeleton jclSkeleton = new JclSkeleton(steps, workspace, ispwApplication, applicationPathNum)

        /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
        jclSkeleton.initialize()

        /* Method referencedCopyBooks will parse the downloaded sources and generate a list of required copy books */
        def copyBookList = referencedCopyBooks(workspace)  

        if(copyBookList.size() > 0)       
        {
            // Get a string with JCL to create a PDS with referenced Copybooks
            def pdsDatasetName  = ispwOwner + '.DEVOPS.ISPW.COPY.PDS'   

            // The createIebcopyCopyBooksJcl will create the JCL for the IEBCOPY job */
            def processJcl      = jclSkeleton.createIebcopyCopyBooksJcl(pdsDatasetName, copyBookList)

            // Submit the JCL created to create a PDS with Copybooks
            steps.topazSubmitFreeFormJcl( 
                connectionId:       "${hciConnId}", 
                credentialsId:      "${hciTokenId}", 
                jcl:                processJcl, 
                maxConditionCode:   '4'
            )
                        
            // Download the generated PDS
            steps.checkout([
                $class:         'PdsConfiguration', 
                connectionId:   "${hciConnId}",
                credentialsId:  "${hciTokenId}",
                fileExtension:  'cpy',
                filterPattern:  "${pdsDatasetName}",
                targetFolder:   "${ispwApplication}/${mfSourceFolder}"
            ])
                                                                        
            // Delete the downloaded Dataset
            processJcl = jclSkeleton.createDeleteTempDsn(pdsDatasetName)

            steps.topazSubmitFreeFormJcl(
                connectionId:       "${hciConnId}",
                credentialsId:      "${hciTokenId}",
                jcl:                processJcl,
                maxConditionCode:   '4'
            )
        }
        else
        {
            steps.echo "No Copy Books to download"
        }
    }

    def getComponents(String cesToken, String container, String containerType)
    {
        steps.echo "Got cesToken ${cesToken}, container ${container}, type ${containerType}"
        def containerTypeText

        switch(containerType) 
        {
            case '0':
                containerTypeText = 'assignments'
            break;
            case '1':
                containerTypeText = 'releases'
            break;
            case '2':
                containerTypeText = 'sets'
            break;
            default:
                steps.echo "Invalid containerType " + containerType
                error "Aborting pipeline"
            break;
        }

        def returnList      = []

        def response        = steps.httpRequest(
            url:                        "${ispwUrl}/ispw/${ispwRuntime}/${containerTypeText}/${container}/tasks",
            consoleLogResponseBody:     false, 
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            
            )

        def jsonSlurper = new JsonSlurper()
        def resp        = jsonSlurper.parseText(response.getContent())
        response        = null
        jsonSlurper     = null

        if(resp.message != null)
        {
            steps.echo "Resp: " + resp.message
            steps.error
        }
        else
        {
            // Compare the taskIds from the set to all tasks in the release 
            // Where they match, determine the assignment and add it to the list of assignments 
            def taskList = resp.tasks

            taskList.each
            {
                if(
                    it.moduleType == 'COB' &&
                    !(returnList.contains(it.moduleName))
                )
                {
                    returnList.add(it.moduleName)
                }
            }
        }

        return returnList
    }

/* 
    Build and return a list of taskIds in the current container, that belong to the desired level
*/
/*
    def ArrayList getSetTaskIdList(String cesToken, String level)
    {
        def returnList  = []

        def response = steps.httpRequest(
            url:                        "${ispwUrl}/ispw/${ispwRuntime}/sets/${ispwContainer}/tasks",
            httpMode:                   'GET',
            consoleLogResponseBody:     false,
            customHeaders:              [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            )

        def jsonSlurper = new JsonSlurper()
        def resp        = jsonSlurper.parseText(response.getContent())
        response        = null
        jsonSlurper     = null

        if(resp.message != null)
        {
            steps.echo "Resp: " + resp.message
            steps.error
        }
        else
        {
            def taskList = resp.tasks

            taskList.each
            {
                // Add taskId only if the component is a COBOL program and is on the desired level 
                if(it.moduleType == 'COB' && it.level == level)
                {
                    returnList.add(it.taskId)
                }
            }
        }

        return returnList
    
    }
*/
    /* Parse downloaded sources and get a list of copy books */
    def List referencedCopyBooks(String workspace) 
    {

        steps.echo "Get all .cbl in current workspace"
        
        // findFiles method requires the "Pipeline Utilities Plugin"
        // Get all Cobol Sources in the MF_Source folder into an array 
        def listOfSources   = steps.findFiles(glob: "**/${ispwApplication}/${mfSourceFolder}/*.cbl")
        def listOfCopybooks = []
        def lines           = []
        def cbook           = /\bCOPY\b/
        //def include         = /\bINCLUDE\b/
        def tokenItem       = ''
        def seventhChar     = ''
        def lineToken       = ''

        // Define a empty array for the list of programs
        listOfSources.each 
        {
            steps.echo "Scanning Program: ${it}"
            def cpyFile = "${workspace}\\${it}"

            File file = new File(cpyFile)

            if (file.exists()) 
            {
                lines = file.readLines().findAll({book -> book =~ /$cbook/}) //+ (file.readLines().findAll({book -> book =~ /$include/}))

                lines.each 
                {
                    
                    lineToken   = it.toString().tokenize()
                    seventhChar = ""

                    if (lineToken.get(0).toString().length() >= 7) 
                    {
                        seventhChar = lineToken.get(0).toString()[6]
                    }
                        
                    for(int i=0;i<lineToken.size();i++) 
                    {
                        tokenItem = lineToken.get(i).toString()

                        steps.echo tokenItem

                        if (
                            tokenItem == "COPY" && seventhChar != "*" ||
                            tokenItem == "INCLUDE" && seventhChar != "*"
                            ) 
                        {
                            steps.echo "Copybook: ${lineToken.get(i+1)}"
                            tokenItem = lineToken.get(i+1).toString()
        
                            if (tokenItem.endsWith(".")) 
                            {
                                listOfCopybooks.add(tokenItem.substring(0,tokenItem.size()-1))
                            }
                            else 
                            {
                                listOfCopybooks.add(tokenItem)
                            }
                                
                        i = lineToken.size()
                        }
                    }    
                }
            }
        }

        return listOfCopybooks

    }      

    def regressTask(taskName, cesToken)
    {
        def requestBodyParm = '''{
            "runtimeConfiguration": "''' + ispwRuntime + '''"
        }'''

        steps.httpRequest(
                url:                    "${ispwUrl}/ispw/${ispwRuntime}/assignments/${ispwAssignment}/tasks/regress?level=${ispwTargetLevel}&mname=${taskName}&mtype=COB",
                httpMode:               'POST',
                consoleLogResponseBody: true,
                contentType:            'APPLICATION_JSON',
                requestBody:            requestBodyParm,
                customHeaders:          [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            )
    }

    /* Regress a list of assignments */
    def regressAssignmentList(assignmentList, cesToken)
    {
        for(int i = 0; i < assignmentList.size(); i++)
        {

            steps.echo "Regress Assignment ${assignmentList[0].toString()}, Level ${ispwTargetLevel}"

            regressAssignment(assignmentList[i], cesToken)

        }
            
    }

    /* Regress one assigment */
    def regressAssignment(assignment, cesToken)
    {
        def requestBodyParm = '''{
            "runtimeConfiguration": "''' + ispwRuntime + '''"
        }'''

        steps.httpRequest(
                url:                    "${ispwUrl}/ispw/${ispwRuntime}/assignments/${assignment}/tasks/regress?level=${ispwTargetLevel}",
                httpMode:               'POST',
                consoleLogResponseBody: true,
                contentType:            'APPLICATION_JSON',
                requestBody:            requestBodyParm,
                customHeaders:          [[
                                        maskValue:  true, 
                                        name:       'authorization', 
                                        value:      "${cesToken}"
                                        ]]
            )
    }
}