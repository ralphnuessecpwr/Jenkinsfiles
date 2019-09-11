package com.compuware.devops.config

/* 
    Pipeline execution specific and server specific parameters which are use throughout the pipeline
*/
class PipelineConfig implements Serializable
{
    def steps
    def mailListLines
    def mailListMap = [:]

    private String configPath           = 'pipeline'            // Path containing config files after downloading them from Git Hub Repository 'config\\pipeline'
    private String pipelineConfigFile   = 'pipeline.config'     // Config file containing pipeline configuration
    private String tttGitConfigFile     = 'tttgit.config'       // Config gile containing for TTT projects stroed in Git Hub

    private String workspace

/* Environment specific settings, which differ between Jenkins servers and applications, but not between runs */
    public String gitTargetBranch                               // Used for synchronizing TTT project stored in Git with programs stored in ISPW
    public String gitBranch                                     // Used for synchronizing TTT project stored in Git with programs stored in ISPW
    
    public String sqScannerName                                 // Sonar Qube Scanner Tool name as defined in "Manage Jenkins" -> "Global Tool Configuration" -> "SonarQube scanner"
    public String sqServerName                                  // Sonar Qube Scanner Server name as defined in "Manage Jenkins" -> "Configure System" -> "SonarQube servers"
    public String sqServerUrl                                   // URL to the SonarQube server
    public String sqHttpRequestAuthHeader                       // Value for Authorization header for http Requests to SonarQube
    public String xaTesterUrl                                   // URL to the XATester repository
    public String xaTesterEnvId                                 // XATester Environment ID
    public String mfSourceFolder                                // Folder containing sources after downloading from ISPW
    public String xlrTemplate                                   // XL Release template to start
    public String xlrUser                                       // XL Release user to use
    public String tttFolder                                     // Folder containing TTT projects after downloading from Git Hub
    public String ispwUrl                                       // ISPW/CES URL for native REST API calls
    public String ispwRuntime                                   // ISPW Runtime

/* Runtime specific settings, which differ runs and get passed as parameters or determined during execution */
    public String ispwStream
    public String ispwApplication
    public String ispwRelease
    public String ispwAssignment
    public String ispwSetId
    public String ispwSrcLevel
    public String ispwTargetLevel
    public String ispwOwner         
    public String applicationPathNum

    public String gitProject        
    public String gitCredentials    
    public String gitUrl            
    public String gitTttRepo        
    public String gitTttUtRepo        
    public String gitTttFtRepo        

    public String cesTokenId
    public String hciConnId         
    public String hciTokenId        
    public String ccRepository      

    public String tttJcl 
      
    public String mailRecipient 

    def PipelineConfig(steps, workspace, params, mailListLines)
    {
        //configGitBranch    = params.Config_Git_Branch
        steps                       = steps
        workspace                   = workspace
        mailListLines               = mailListLines

        ispwStream                  = params.ISPW_Stream
        ispwApplication             = params.ISPW_Application
        ispwRelease                 = params.ISPW_Release
        ispwAssignment              = params.ISPW_Assignment
        ispwSetId                   = params.ISPW_Set_Id
        ispwOwner                   = params.ISPW_Owner        
        ispwSrcLevel                = params.ISPW_Src_Level

        applicationPathNum          = ispwSrcLevel.charAt(ispwSrcLevel.length() - 1)

        ispwTargetLevel             = "QA" + applicationPathNum
        tttJcl                      = "Runner_PATH" + applicationPathNum + ".jcl"

        sqHttpRequestAuthHeader     = params.SQ_SERVER_AUTH_TOKEN

        gitProject                  = params.Git_Project
        gitCredentials              = params.Git_Credentials
        
        gitUrl                      = "https://github.com/${gitProject}"
        gitTttRepo                  = "${ispwStream}_${ispwApplication}_Unit_Tests.git"
        gitTttUtRepo                = "${ispwStream}_${ispwApplication}_Unit_Tests.git"
        gitTttFtRepo                = "${ispwStream}_${ispwApplication}_Functional_Tests.git"

        cesTokenId                  = params.CES_Token
        hciConnId                   = params.HCI_Conn_ID
        hciTokenId                  = params.HCI_Token
        ccRepository                = params.CC_repository
    }

    /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
    def initialize()
    {
        setServerConfig()

        setTttGitConfig()

        setMailConfig()    
    }

    /* Read configuration values from pipeline.config file */
    def setServerConfig()
    {
        def lineToken
        def parmName
        def parmValue

        def lines = readConfigFile("${pipelineConfigFile}")

        lines.each
        {
            if(
                it.toString().indexOf('#') != 0 &&
                it.toString().trim() != ''
            )
            {
                lineToken   = it.toString().tokenize("=")
                parmName    = lineToken.get(0).toString()
                parmValue   = lineToken.get(1).toString().trim()

                switch(parmName)
                {
                    case "SQ_SCANNER_NAME":
                        sqScannerName   = parmValue
                        break;
                    case "SQ_SERVER_NAME": 
                        sqServerName    = parmValue
                        break;
                    case "SQ_SERVER_URL":
                        sqServerUrl     = parmValue
                        break;
                    case "MF_SOURCE_FOLDER":
                        mfSourceFolder  = parmValue
                        break;
                    case "XLR_TEMPLATE":
                        xlrTemplate     = parmValue
                        break;
                    case "XLR_USER":
                        xlrUser         = parmValue
                        break;
                    case "TTT_FOLDER":
                        tttFolder       = parmValue
                        break;
                    case "ISPW_URL":
                        ispwUrl         = parmValue
                        break;
                    case "ISPW_RUNTIME":
                        ispwRuntime     = parmValue
                        break;
                    default:
                        steps.echo "Found unknown Pipeline Parameter " + parmName + " " + parmValue + "\nWill ignore and continue."
                        break;
                }
            }
        }
    }


    /* Read configuration values from tttgit.config file */
    def setTttGitConfig()
    {
        def lineToken
        def parmName
        def parmValue

        def lines = readConfigFile("${tttGitConfigFile}")

        lines.each
        {
            if(
                it.toString().indexOf('#') != 0 &&
                it.toString().trim() != ''
            )
            {
                lineToken   = it.toString().tokenize("=")
                parmName    = lineToken.get(0).toString()
                parmValue   = lineToken.get(1).toString().trim()

                switch(parmName)
                {
                    case "TTT_GIT_TARGET_BRANCH":
                        gitTargetBranch = parmValue
                        break;
                    case "TTT_GIT_BRANCH": 
                        gitBranch       = parmValue
                        break;
                    case "TTT_FT_SERVER_URL":
                        xaTesterUrl     = parmValue
                        break;
                    case "TTT_FT_ENVIRONMENT_ID":
                        xaTesterEnvId   = parmValue
                        break;
                    default:
                        steps.echo "Found unknown TTT Parameter " + parmName + " " + parmValue + "\nWill ignore and continue."
                        break;
                }
            }
        }
    }

    /* Read list of email addresses from config file */
    def setMailConfig()
    {        
        def lineToken
        def tsoUser
        def emailAddress

        mailListLines.each
        {
            lineToken       = it.toString().tokenize(":")
            tsoUser         = lineToken.get(0).toString()
            emailAddress    = lineToken.get(1).toString().trim()

            mailListMap."${tsoUser}" = "${emailAddress}"
        }

        mailRecipient  = mailListMap[(ispwOwner.toUpperCase())]
    }
    
    def readConfigFile(String fileName)
    {        
        
        def filePath    = "${configPath}/${fileName}"

        steps.echo "Path: " + filePath

        def fileText    = steps.libraryResource filePath

        return fileText.tokenize("\n")
    }
}