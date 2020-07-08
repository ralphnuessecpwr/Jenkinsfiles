package com.compuware.devops.util

class TttHelper implements Serializable {

    def script
    def steps
    def pConfig

    JclSkeleton jclSkeleton 

    def listOfScenarios
    def listOfSources
    def listOfPrograms 
    def listOfTttProjects

    TttHelper(script, steps, pConfig) 
    {
        this.script     = script
        this.steps      = steps
        this.pConfig    = pConfig

        jclSkeleton     = new JclSkeleton(steps, script.workspace, pConfig.ispwApplication, pConfig.applicationPathNum)
    }

    /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
    def initialize()
    {
        jclSkeleton.initialize()

        // Get all Cobol Sources in the MF_Source folder into an array 
        this.listOfSources       = steps.findFiles(glob: "**/${pConfig.ispwApplication}/${pConfig.mfSourceFolder}/*.cbl")

        // Define empty arrays for the list of programs and list of unit test projects in Git
        this.listOfPrograms     = []
        this.listOfTttProjects  = []

        // Determine program names for each source member
        listOfSources.each
        {
            // The split method uses regex to search for patterns, therefore
            // Backslashes, Dots and Underscores which mean certain patterns in regex need to be escaped 
            // The backslash in Windows paths is duplicated in Java, therefore it need to be escaped twice
            // Trim ./cbl from the Source members to populate the array of program names
            def programName = it.name.trim().split("\\.")[0]
            listOfPrograms.add(programName)
            listOfTttProjects.add(programName + "_Unit_Tests")
        }
    }

    // The testSuite parameter value "All_Scenarios" will execute all .testscenario files found in the projectFolder. 
    // If that folder contains more than one TTT project, recursive true will recursively search through all sub folders for .testscenario files
    def loopThruScenarios(){

        steps.totaltestUT ccClearStats:     false,
                ccRepo:                     "${pConfig.ccRepository}", 
                ccSystem:                   "${pConfig.ispwApplication}", 
                ccTestId:                   "${script.BUILD_NUMBER}", 
                connectionId:               "${pConfig.hciConnId}", 
                credentialsId:              "${pConfig.hciTokenId}", 
                hlq:                        '', 
                jcl:                        "${pConfig.tttJcl}", 
                projectFolder:              "${pConfig.tttFolder}", 
                recursive:                  true, 
                testSuite:                  "All_Scenarios"
    
    }

    def executeFunctionalTests()
    {
        steps.totaltest credentialsId:          "${pConfig.hciTokenId}", 
            environmentId:                      "${pConfig.xaTesterEnvId}", 
            folderPath:                         '', 
            serverUrl:                          "${pConfig.ispwUrl}", 
            stopIfTestFailsOrThresholdReached:  false,
            sonarVersion:                       '6'
    }

    def passResultsToJunit()
    {
        // Process the Total Test Junit result files into Jenkins
        steps.junit allowEmptyResults:    true, 
            keepLongStdio:                true,
            healthScaleFactor:            0.0,  
            testResults:                  "TTTUnit/*.xml"
    }

    def collectCodeCoverageResults()
    {
        // Code Coverage needs to match the code coverage metrics back to the source code in order for them to be loaded in SonarQube
        // The source variable is the location of the source that was downloaded from ISPW
        def sources="${pConfig.ispwApplication}\\${pConfig.mfSourceFolder}"

        // The Code Coverage Plugin passes it's primary configuration in the string or a file
        def ccproperties = 'cc.sources=' + sources + 
            '\rcc.repos=' + pConfig.ccRepository + 
            '\rcc.system=' + pConfig.ispwApplication  + 
            '\rcc.test=' + script.BUILD_NUMBER +
            '\rcc.ddio.overrides=SALESSUP.RXN3.DEV1.LOAD.SSD,' +
            'SALESSUP.RXN3.DEV2.LOAD.SSD,' + 
            'SALESSUP.RXN3.DEV3.LOAD.SSD,' + 
            'SALESSUP.RXN3.QA1.LOAD.SSD,' + 
            'SALESSUP.RXN3.QA2.LOAD.SSD,' + 
            'SALESSUP.RXN3.QA3.LOAD.SSD,' + 
            'SALESSUP.RXN3.STG.LOAD.SSD,' + 
            'SALESSUP.RXN3.PRD.LOAD.SSD'

        steps.step([
            $class:                   'CodeCoverageBuilder',
                analysisProperties:         ccproperties,           // Pass in the analysisProperties as a string
                analysisPropertiesPath:     '',                     // Pass in the analysisProperties as a file.  Not used in this example
                connectionId:               "${pConfig.hciConnId}", 
                credentialsId:              "${pConfig.hciTokenId}"
        ])
    }

    def cleanUpCodeCoverageResults()
    {
        int testId = Integer.parseInt(script.BUILD_NUMBER) - 1

        steps.echo "Cleaning up Code Coverage results from previous job execution"
        steps.echo "Determined Test ID " + testId

        def cleanupJcl = jclSkeleton.createCleanUpCcRepo(pConfig.ispwApplication, testId.toString())

        steps.topazSubmitFreeFormJcl connectionId:  pConfig.hciConnId, 
            credentialsId:                          pConfig.hciTokenId, 
            jcl:                                    cleanupJcl, 
            maxConditionCode:                       '8'
    }
}