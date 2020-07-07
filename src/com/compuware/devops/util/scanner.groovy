def sqServerName =                          // use name of the SonarQube server defined in Jenkins / Configure Systems / SonarQube server section
def pipelineType =                          // use 'UT' for a scan with VT resluts, and 'FT' for a scan with NVT results. This is because results will reside in different folders
def projectName  = 'Sonar_Demo_Project'
def sourceFolder =                          // Set to folder containing the program sources. For ISPW this usually is <application_name>/MF_Source
def testPath
def coveragePath

private String determineUtResultPath()
{
    // Finds all of the Total Test results files that will be submitted to SonarQube
    def tttListOfResults    = steps.findFiles(glob: 'TTTSonar/*.xml')   // Total Test SonarQube result files are stored in TTTSonar directory

    // Build the sonar testExecutionReportsPaths property
    // Start empty
    def testResults         = ""    

    // Loop through each result Total Test results file found
    tttListOfResults.each 
    {
        testResults         = testResults + "TTTSonar/" + it.name +  ',' // Append the results file to the property
    }

    return testResults
}

switch(pipelineType)
{
    case "UT":
        testPath        = 'tests'
        resultPath      = determineUtResultPath()           // For VT there may be more that one result .xml
        coveragePath    = "Coverage/CodeCoverage.xml"
        break;
    case "FT":
        testPath        = 'TTTReport'                       
        resultPath      = 'TTTReport/SonarTestReport.xml'   
        coveragePath    = ''                                // I haven't used CoCo with NVT in a pipeline yet. 
        break;
    default:
        steps.echo "Received wrong pipelineType: " + pipelineType
        steps.echo "Valid types are 'UT' or FT"
        break;
}

withSonarQubeEnv("${sqServerName}")       
{
    // Build parameter list for Sonar Scanner.
    // Test definitions
    def sqScannerProperties   = ' -Dsonar.tests=' + testPath

    // Test results
    sqScannerProperties       = sqScannerProperties + " -Dsonar.testExecutionReportPaths=${testResultPath}"

    // If the CoCo path has been set, add it to the properties
    if(coveragePath != '')
    {
        sqScannerProperties       = sqScannerProperties + " -Dsonar.coverageReportPaths=${coveragePath}"
    }

    // SonarQube project to load results into
    sqScannerProperties       = sqScannerProperties + " -Dsonar.projectKey=${projectName} -Dsonar.projectName=${projectName} -Dsonar.projectVersion=1.0"
    // Location of the Cobol Source Code to scan
    sqScannerProperties       = sqScannerProperties + " -Dsonar.sources=${sourceFolder}"
    // Location of the Cobol copybooks to scan
    sqScannerProperties       = sqScannerProperties + " -Dsonar.cobol.copy.directories=${sourceFolder}"  
    // File extensions for Cobol and Copybook files.  The Total Test files need that contain tests need to be defined as cobol for SonarQube to process the results
    sqScannerProperties       = sqScannerProperties + " -Dsonar.cobol.file.suffixes=cbl,testsuite,testscenario,stub,result -Dsonar.cobol.copy.suffixes=cpy -Dsonar.sourceEncoding=UTF-8"
    
    // Call the SonarQube Scanner with properties defined above
    steps.bat "${scannerHome}/bin/sonar-scanner" + sqScannerProperties
}
