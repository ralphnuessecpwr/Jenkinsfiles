package com.compuware.devops.util

class TttHelper implements Serializable {

    def script
    def steps
    def pConfig

    JclSkeleton jclSkeleton 

    def listOfScenarios
    def listOfSources
    def listOfPrograms 
    def listOfUtProjects
    def listOfFtProjects

    TttHelper(script, steps, pConfig){
        this.script     = script
        this.steps      = steps
        this.pConfig    = pConfig

        jclSkeleton     = new JclSkeleton(steps, script.workspace, pConfig.ispw.application, pConfig.ispw.applicationPathNum.toString())
    }

    /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
    def initialize(){
        jclSkeleton.initialize()

        // findFiles method requires the "Pipeline Utilities Plugin"
        // Get all testscenario files in the current workspace into an array
        this.listOfScenarios  = steps.findFiles(glob: '**/*.testscenario')

        steps.echo "Found Scenarios " + listOfScenarios.toString()
        // Get all Cobol Sources in the MF_Source folder into an array 
        this.listOfSources       = steps.findFiles(glob: "**/${pConfig.ispw.application}/${pConfig.ispw.localFolder}/*.cbl")

        // Define empty arrays for the list of programs and list of unit test projects in Git
        this.listOfPrograms     = []
        this.listOfUtProjects   = []
        this.listOfFtProjects   = []

        // Determine program names for each source member
        listOfSources.each{
            // The split method uses regex to search for patterns, therefore
            // Backslashes, Dots and Underscores which mean certain patterns in regex need to be escaped 
            // The backslash in Windows paths is duplicated in Java, therefore it need to be escaped twice
            // Trim ./cbl from the Source members to populate the array of program names
            def programName = it.name.trim().split("\\.")[0]
            listOfPrograms.add(programName)
            listOfUtProjects.add(programName + "_Unit_Tests")
            listOfUtProjects.add(programName + "_Functional_Tests")
        }
    }

    // The testSuite parameter value "All_Scenarios" will execute all .testscenario files found in the projectFolder. 
    // If that folder contains more than one TTT project, recursive true will recursively search through all sub folders for .testscenario files
    def loopThruScenarios(){

        // Loop through all downloaded Topaz for Total Test scenarios
        listOfScenarios.each
        {
            // Get root node of the path, i.e. the name of the Total Test project
            def scenarioPath        = it.path // Fully qualified name of the Total Test Scenario file
            def projectName         = it.path.trim().split("\\\\")[0] + "\\"+ it.path.trim().split("\\\\")[1]  // Total Test Project name is the root folder of the full path to the testscenario 
            def jclFolder           = script.workspace + "\\" + projectName + '\\Unit Test\\JCL'   // Path containing Runner.jcl
            def scenarioFullName    = it.name  // Get the full name of the testscenario file i.e. "name.testscenario"
            def scenarioName        = it.name.trim().split("\\.")[0]  // Get the name of the scenario file without ".testscenario"
            def scenarioTarget      = scenarioName.split("\\_")[0]  // Target Program will be the first part of the scenario name (convention)
    
            // For each of the scenarios walk through the list of source files and determine if the target matches one of the programs
            // In that case, execute the unit test.  Determine if the program name matches the target of the Total Test scenario
            if(listOfPrograms.contains(scenarioTarget))
            {
                // Log which 
                steps.echo "*************************\n" +
                    "Scenario " + scenarioFullName + '\n' +
                    "Path " + scenarioPath + '\n' +
                    "Project " + projectName + '\n' +
                    "*************************"
            
                def jclJobCardPath = jclFolder + '\\JobCard.jcl' 

                steps.writeFile(file: jclJobCardPath, text: jclSkeleton.jobCardJcl)

                steps.step([
                    $class:       'TotalTestBuilder', 
                        ccClearStats:   false,                          // Clear out any existing Code Coverage stats for the given ccSystem and ccTestId
                        ccRepo:         "${pConfig.coco.repository}",
                        ccSystem:       "${pConfig.ispw.application}", 
                        ccTestId:       "${script.BUILD_NUMBER}",              // Jenkins environment variable, resolves to build number, i.e. #177 
                        credentialsId:  "${pConfig.hci.hostToken}", 
                        deleteTemp:     true,                           // (true|false) Automatically delete any temp files created during the execution
                        hlq:            '',                             // Optional - high level qualifier used when allocation datasets
                        connectionId:   "${pConfig.hci.connectionId}",    
                        jcl:            "${pConfig.ttt.runnerJcl}",            // Name of the JCL file in the Total Test Project to execute
                        projectFolder:  "${projectName}",            // Name of the Folder in the file system that contains the Total Test Project.  
//                        recursive:      true,
                        testSuite:      "${scenarioFullName}",       // Name of the Total Test Scenario to execute
                        useStubs:       true                            // (true|false) - Execute with or without stubs
                ])                   
            }
        }

        // steps.totaltestUT ccClearStats:     false,
        //         ccRepo:                     "${pConfig.coco.repository}", 
        //         ccSystem:                   "${pConfig.ispw.application}", 
        //         ccTestId:                   "${script.BUILD_NUMBER}", 
        //         connectionId:               "${pConfig.hci.connectionId}", 
        //         credentialsId:              "${pConfig.hci.hostToken}", 
        //         hlq:                        '', 
        //         jcl:                        "${pConfig.ttt.runnerJcl}", 
        //         projectFolder:              "${pConfig.ttt.utFolder}", 
        //         recursive:                  true, 
        //         testSuite:                  "All_Scenarios"
    
    }

    def executeFunctionalTests(){
        steps.totaltest credentialsId:          "${pConfig.hci.hostToken}", 
            environmentId:                      "${pConfig.ttt.ftEnvironment}", 
            folderPath:                         'tests', 
            serverUrl:                          "${pConfig.ispw.url}", 
            stopIfTestFailsOrThresholdReached:  false,
            sonarVersion:                       '6'
    }

    def passResultsToJunit(){
        // Process the Total Test Junit result files into Jenkins
        steps.junit allowEmptyResults:    true, 
            keepLongStdio:                true,
            healthScaleFactor:            0.0,  
            testResults:                  "TTTUnit/*.xml"
    }

    def collectCodeCoverageResults(){
        // Code Coverage needs to match the code coverage metrics back to the source code in order for them to be loaded in SonarQube
        // The source variable is the location of the source that was downloaded from ISPW
        def sources="${pConfig.ispw.application}/${pConfig.ispw.localFolder}"

        // The Code Coverage Plugin passes it's primary configuration in the string or a file
        def ccproperties = 'cc.sources=' + sources + 
            '\rcc.repos=' + pConfig.coco.repository + 
            '\rcc.system=' + pConfig.ispw.application  + 
            '\rcc.test=' + script.BUILD_NUMBER +
            '\rcc.ddio.overrides=' + pConfig.coco.ddioOverridesCommaList

        steps.step(
            [
                $class:                     'CodeCoverageBuilder',
                analysisProperties:         ccproperties,           // Pass in the analysisProperties as a string
                analysisPropertiesPath:     '',                     // Pass in the analysisProperties as a file.  Not used in this example
                connectionId:               "${pConfig.hci.connectionId}", 
                credentialsId:              "${pConfig.hci.hostToken}"
            ]
        )
    }

    def collectCodeCoverageResults(pipelineType){

        // Code Coverage needs to match the code coverage metrics back to the source code in order for them to be loaded in SonarQube
        // The source variable is the location of the source that was downloaded from ISPW
        def sources="${pConfig.ispw.application}/${pConfig.ispw.localFolder}"

        def testId

        switch(pipelineType){

            case "UT":
                testId          = script.BUILD_NUMBER
            break;

            case "FT":
                testId          = "JENKINS"
            break;

            default:
                steps.echo "TttHelper.collectCodeCoverageResults received wrong pipelineType: " + pipelineType
                steps.echo "Valid types are 'UT' or FT"
            break;

        }

        // The Code Coverage Plugin passes it's primary configuration in the string or a file
        def ccproperties = 'cc.sources=' + sources + 
            '\rcc.repos=' + pConfig.coco.repository + 
            '\rcc.system=' + pConfig.ispw.application  + 
            '\rcc.test=' + testId +
            '\rcc.ddio.overrides=' + pConfig.coco.ddioOverridesCommaList

        steps.step(
            [
                $class:                     'CodeCoverageBuilder',
                analysisProperties:         ccproperties,           // Pass in the analysisProperties as a string
                analysisPropertiesPath:     '',                     // Pass in the analysisProperties as a file.  Not used in this example
                connectionId:               "${pConfig.hci.connectionId}", 
                credentialsId:              "${pConfig.hci.hostToken}"
            ]
        )
    }

    def cleanUpCodeCoverageResults(){
        int testId = Integer.parseInt(script.BUILD_NUMBER) - 1

        steps.echo "Cleaning up Code Coverage results from previous job execution"
        steps.echo "Determined Test ID " + testId

        def cleanupJcl = jclSkeleton.createCleanUpCcRepo(pConfig.ispw.application, testId.toString())

        steps.topazSubmitFreeFormJcl connectionId:  pConfig.hci.connectionId, 
            credentialsId:                          pConfig.hci.hostToken, 
            jcl:                                    cleanupJcl, 
            maxConditionCode:                       '8'
    }
}