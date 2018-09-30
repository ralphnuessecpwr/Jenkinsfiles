package com.compuware.devops.util

/**
 Wrapper around the Git Plugin's Checkout Method
 @param URL - URL for the git server
 @param Branch - The branch that will be checked out of git
 @param Credentials - Jenkins credentials for logging into git
 @param Folder - Folder relative to the workspace that git will check out files into
*/
class SonarHelper implements Serializable {

    def script
    def steps
    def scannerHome

    SonarHelper(script, steps, pConfig) 
    {
        this.script         = script
        this.steps          = steps
    }

    def initialize()
    {
        this.scannerHome    = steps.tool "${pConfig.sqScannerName}";
    }

    def scan()
    {
        withSonarQubeEnv("${pConfig.sqServerName}")       // 'localhost' is the name of the SonarQube server defined in Jenkins / Configure Systems / SonarQube server section
        {
            // Finds all of the Total Test results files that will be submitted to SonarQube
            def TTTListOfResults    = steps.findFiles(glob: 'TTTSonar/*.xml')   // Total Test SonarQube result files are stored in TTTSonar directory

            // Build the sonar testExecutionReportsPaths property
            // Start will the property itself
            def SQ_TestResult       = "-Dsonar.testExecutionReportPaths="    

            // Loop through each result Total Test results file found
            TTTListOfResults.each 
            {
                def TTTResultName   = it.name   // Get the name of the Total Test results file   
                SQ_TestResult       = SQ_TestResult + "TTTSonar/" + it.name +  ',' // Append the results file to the property
            }

            // Build the rest of the SonarQube Scanner Properties
            
            // Test and Coverage results
            def SQ_Scanner_Properties   = " -Dsonar.tests=tests ${SQ_TestResult} -Dsonar.coverageReportPaths=Coverage/CodeCoverage.xml"
            // SonarQube project to load results into
            SQ_Scanner_Properties       = SQ_Scanner_Properties + " -Dsonar.projectKey=${script.JOB_NAME} -Dsonar.projectName=${script.JOB_NAME} -Dsonar.projectVersion=1.0"
            // Location of the Cobol Source Code to scan
            SQ_Scanner_Properties       = SQ_Scanner_Properties + " -Dsonar.sources=${pConfig.ispwApplication}\\${pConfig.mfSourceFolder}"
            // Location of the Cobol copybooks to scan
            SQ_Scanner_Properties       = SQ_Scanner_Properties + " -Dsonar.cobol.copy.directories=${pConfig.ispwApplication}\\${pConfig.mfSourceFolder}"  
            // File extensions for Cobol and Copybook files.  The Total Test files need that contain tests need to be defined as cobol for SonarQube to process the results
            SQ_Scanner_Properties       = SQ_Scanner_Properties + " -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub -Dsonar.cobol.copy.suffixes=cpy -Dsonar.sourceEncoding=UTF-8"
            
            // Call the SonarQube Scanner with properties defined above
            steps.bat "${scannerHome}/bin/sonar-scanner" + SQ_Scanner_Properties
        }
    
    }

}