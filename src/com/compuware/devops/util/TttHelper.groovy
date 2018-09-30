package com.compuware.devops.util

class TttHelper implements Serializable {

    def steps
    def pConfig

    def listOfScenarios
    def listOfSources
    def listOfPrograms 

    TttHelper(steps, pConfig) 
    {
        this.steps      = steps
        this.pConfig    = pConfig

        // findFiles method requires the "Pipeline Utilities Plugin"
        // Get all testscenario files in the current workspace into an array
        this.tttListOfScenarios  = findFiles(glob: '**/*.testscenario')

        // Get all Cobol Sources in the MF_Source folder into an array 
        this.listOfSources       = findFiles(glob: "**/${pConfig.ispwApplication}/${pConfig.mfSourceFolder}/*.cbl")

        // Define a empty array for the list of programs
        this.listOfPrograms      = []

        // Determine program names for each source member
        listOfSources.each
        {
            // The split method uses regex to search for patterns, therefore
            // Backslashes, Dots and Underscores which mean certain patterns in regex need to be escaped 
            // The backslash in Windows paths is duplicated in Java, therefore it need to be escaped twice
            // Trim ./cbl from the Source members to populate the array of program names
            listOfPrograms.add(it.name.trim().split("\\.")[0])
        }
    }

    def loopThruScenarios()
    {

        // Loop through all downloaded Topaz for Total Test scenarios
        tttListOfScenarios.each
        {

            // Get root node of the path, i.e. the name of the Total Test project
            def tttScenarioPath        = it.path // Fully qualified name of the Total Test Scenario file
            def tttProjectName         = it.path.trim().split("\\\\")[0] + "\\"+ it.path.trim().split("\\\\")[1]  // Total Test Project name is the root folder of the full path to the testscenario 
            def tttScenarioFullName    = it.name  // Get the full name of the testscenario file i.e. "name.testscenario"
            def tttScenarioName        = it.name.trim().split("\\.")[0]  // Get the name of the scenario file without ".testscenario"
            def tttScenarioTarget      = tttScenarioName.split("\\_")[0]  // Target Program will be the first part of the scenario name (convention)
    
            // For each of the scenarios walk through the list of source files and determine if the target matches one of the programs
            // In that case, execute the unit test.  Determine if the program name matches the target of the Total Test scenario
            if(listOfPrograms.contains(tttScenarioTarget))
            {
                // Log which 
                println "*************************\n" +
                    "Scenario " + tttScenarioFullName + '\n' +
                    "Path " + tttScenarioPath + '\n' +
                    "Project " + tttProjectName + '\n' +
                    "*************************"
            
                steps.step([
                    $class:       'TotalTestBuilder', 
                        ccClearStats:   false,                          // Clear out any existing Code Coverage stats for the given ccSystem and ccTestId
                        ccRepo:         "${pConfig.ccRepository}",
                        ccSystem:       "${pConfig.ispwApplication}", 
                        ccTestId:       "${steps.BUILD_NUMBER}",              // Jenkins environment variable, resolves to build number, i.e. #177 
                        credentialsId:  "${pConfig.hciTokenId}", 
                        deleteTemp:     true,                           // (true|false) Automatically delete any temp files created during the execution
                        hlq:            '',                             // Optional - high level qualifier used when allocation datasets
                        connectionId:   "${pConfig.hciConnId}",    
                        jcl:            "${pConfig.tttJcl}",            // Name of the JCL file in the Total Test Project to execute
                        projectFolder:  "${tttProjectName}",            // Name of the Folder in the file system that contains the Total Test Project.  
                        testSuite:      "${tttScenarioFullName}",       // Name of the Total Test Scenario to execute
                        useStubs:       true                            // (true|false) - Execute with or without stubs
                ])                   
            }
        }
    }

    def passResultsToJunit()
    {
        // Process the Total Test Junit result files into Jenkins
        junit allowEmptyResults:    true, 
            keepLongStdio:          true, 
            testResults:            "TTTUnit/*.xml"
    }

}