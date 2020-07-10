package com.compuware.devops.util
import groovy.json.JsonSlurper

/**
 Wrapper around the Sonar Qube activities and the Sonar Scanner
*/
class SonarHelper implements Serializable {

    def script
    def steps
    def scannerHome
    def pConfig
    def httpRequestAuthorizationHeader

    SonarHelper(script, steps, pConfig){
        this.script                         = script
        this.steps                          = steps
        this.pConfig                        = pConfig
    }

    /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
    def initialize(){
        this.scannerHome    = steps.tool "${pConfig.sonar.scannerName}";
    }

    def scan(){
        def testResults = determineUtResultPath()

        runScan(testResults, script.JOB_NAME)
    }

    def scan(pipelineType){
        def project
        def testPath
        def resultPath
        def coveragePath

        switch(pipelineType){

            case "UT":
                project         = determineUtProjectName()
                testPath        = 'tests'
                resultPath      = determineUtResultPath()
                coveragePath    = "Coverage/CodeCoverage.xml"
            break;

            case "FT":
                project         = determineFtProjectName()
                testPath        = 'TTTSonar'                       
                resultPath      = 'TTTSonar/generated.cli.suite.sonar.xml'   
                coveragePath    = ''
            break;

            default:
                steps.echo "SonarHelper.scan received wrong pipelineType: " + pipelineType
                steps.echo "Valid types are 'UT' or FT"
            break;

        }

        runScan(testPath, resultPath, coveragePath, project)
    }

    String checkQualityGate(){
        String result

        // Wait for the results of the SonarQube Quality Gate
        steps.timeout(time: 2, unit: 'MINUTES'){                
            // Wait for webhook call back from SonarQube.  SonarQube webhook for callback to Jenkins must be configured on the SonarQube server.
            def sonarGate = steps.waitForQualityGate()

            result = sonarGate.status
        }

        return result
    }

    String determineUtProjectName(){
        return pConfig.ispw.owner + '_' + pConfig.ispw.stream + '_' + pConfig.ispw.application
    }

    String determineFtProjectName(){
        return pConfig.ispw.stream + '_' + pConfig.ispw.application
    }


    private String determineUtResultPath(){
        // Finds all of the Total Test results files that will be submitted to SonarQube
        def tttListOfResults    = steps.findFiles(glob: 'TTTSonar/*.xml')   // Total Test SonarQube result files are stored in TTTSonar directory

        // Build the sonar testExecutionReportsPaths property
        // Start empty
        def testResults         = ""    

        // Loop through each result Total Test results file found
        tttListOfResults.each{
            testResults         = testResults + "TTTSonar/" + it.name +  ',' // Append the results file to the property
        }

        return testResults
    }

    private runScan(testPath, testResultPath, coveragePath, projectName){
        steps.withSonarQubeEnv("${pConfig.sonar.serverHost}"){
            // Test and Coverage results
            def sqScannerProperties   = ' -Dsonar.tests=' + testPath

            sqScannerProperties       = sqScannerProperties + " -Dsonar.testExecutionReportPaths=${testResultPath}"

            if(coveragePath != ''){
                sqScannerProperties       = sqScannerProperties + " -Dsonar.coverageReportPaths=${coveragePath}"
            }

            // SonarQube project to load results into
            sqScannerProperties       = sqScannerProperties + " -Dsonar.projectKey=${projectName} -Dsonar.projectName=${projectName} -Dsonar.projectVersion=1.0"
            // Location of the Cobol Source Code to scan
            sqScannerProperties       = sqScannerProperties + " -Dsonar.sources=${pConfig.ispw.application}/${pConfig.ispw.localFolder}"
            // Location of the Cobol copybooks to scan
            sqScannerProperties       = sqScannerProperties + " -Dsonar.cobol.copy.directories=${pConfig.ispw.application}/${pConfig.ispw.localFolder}"  
            // File extensions for Cobol and Copybook files.  The Total Test files need that contain tests need to be defined as cobol for SonarQube to process the results
            sqScannerProperties       = sqScannerProperties + " -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,result,cpy -Dsonar.cobol.copy.suffixes=cpy -Dsonar.sourceEncoding=UTF-8"
            
            // Call the SonarQube Scanner with properties defined above
            steps.bat "${scannerHome}/bin/sonar-scanner" + sqScannerProperties
        }
    }

    def checkForProject(String projectName){
        def response

        def httpResponse = steps.httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: pConfig.sonar.httpRequestAuthHeader]], 
            ignoreSslErrors:            true, 
            responseHandle:             'NONE', 
            consoleLogResponseBody:     true,
            url:                        "${pConfig.sonar.serverUrl}/api/projects/search?projects=${projectName}"

        def jsonSlurper = new JsonSlurper()
        def httpResp    = jsonSlurper.parseText(httpResponse.getContent())

        httpResponse    = null
        jsonSlurper     = null

        if(httpResp.message != null){
            steps.echo "Resp: " + httpResp.message
            steps.error
        }
        else{
            // Compare the taskIds from the set to all tasks in the release 
            // Where they match, determine the assignment and add it to the list of assignments 
            def pagingInfo = httpResp.paging
            if(pagingInfo.total == 0){
                response = "NOT FOUND"
            }
            else{
                response = "FOUND"
            }
        }

        return response
    }

    def createProject(String projectName){
        def httpResponse = steps.httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: pConfig.sonar.httpRequestAuthHeader]],
            httpMode:                   'POST',
            ignoreSslErrors:            true, 
            responseHandle:             'NONE', 
            consoleLogResponseBody:     true,
            url:                        "${pConfig.sonar.serverUrl}/api/projects/create?project=${projectName}&name=${projectName}"

        def jsonSlurper = new JsonSlurper()
        def httpResp    = jsonSlurper.parseText(httpResponse.getContent())
        
        httpResponse    = null
        jsonSlurper     = null

        if(httpResp.message != null){
            steps.echo "Resp: " + httpResp.message
            steps.error
        }
        else{
            steps.echo "Created SonarQube project ${projectName}."
        }
    }

    def setQualityGate(String qualityGate, String projectName){

        def qualityGateId = getQualityGateId(qualityGate)

        def httpResponse = steps.httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: pConfig.sonar.httpRequestAuthHeader]],
            httpMode:                   'POST',
            ignoreSslErrors:            true, 
            responseHandle:             'NONE', 
            consoleLogResponseBody:     true,
            url:                        "${pConfig.sonar.serverUrl}/api/qualitygates/select?gateId=${qualityGateId}&projectKey=${projectName}"

        steps.echo "Assigned QualityGate ${qualityGate} to project ${projectName}."
    }

    private getQualityGateId(String qualityGateName){

        def response

        def httpResponse = steps.httpRequest customHeaders: [[maskValue: true, name: 'authorization', value: pConfig.sonar.httpRequestAuthHeader]], 
            ignoreSslErrors:            true, 
            responseHandle:             'NONE', 
            consoleLogResponseBody:     true,
            url:                        "${pConfig.sonar.serverUrl}/api/qualitygates/show?name=${qualityGateName}"

        def jsonSlurper = new JsonSlurper()
        def httpResp    = jsonSlurper.parseText(httpResponse.getContent())

        httpResponse    = null
        jsonSlurper     = null

        if(httpResp.message != null){
            steps.echo "Resp: " + httpResp.message
            steps.error
        }
        else{
            response = httpResp.id 
        }

        return response
    }
}
