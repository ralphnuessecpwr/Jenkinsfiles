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
    private String pipelineConfigFile   = 'pipelineConfig.yml'     // Config file containing pipeline configuration
    private String tttGitConfigFile     = 'tttgit.config'       // Config gile containing for TTT projects stroed in Git Hub

    private String workspace

/* Environment specific settings, which differ between Jenkins servers and applications, but not between runs */
    public String gitTargetBranch                               // Used for synchronizing TTT project stored in Git with programs stored in ISPW
    public String gitBranch                                     // Used for synchronizing TTT project stored in Git with programs stored in ISPW
    public String gitFtBranch                                   // Used for synchronizing TTT project stored in Git with programs stored in ISPW
    
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
        this.steps                       = steps
        this.workspace                   = workspace
        this.mailListLines               = mailListLines

        this.ispwStream                  = params.ISPW_Stream
        this.ispwApplication             = params.ISPW_Application
        this.ispwRelease                 = params.ISPW_Release
        this.ispwAssignment              = params.ISPW_Assignment
        this.ispwSetId                   = params.ISPW_Set_Id
        this.ispwOwner                   = params.ISPW_Owner        
        this.ispwSrcLevel                = params.ISPW_Src_Level

        this.applicationPathNum          = ispwSrcLevel.charAt(ispwSrcLevel.length() - 1)

        this.ispwTargetLevel             = "QA" + applicationPathNum
        this.tttJcl                      = "Runner_PATH" + applicationPathNum + ".jcl"

        this.sqHttpRequestAuthHeader     = params.SQ_SERVER_AUTH_TOKEN

        this.gitProject                  = params.Git_Project
        this.gitCredentials              = params.Git_Credentials
        
        this.gitUrl                      = "https://github.com/${gitProject}"
        this.gitTttRepo                  = "${ispwStream}_${ispwApplication}_Unit_Tests.git"
        this.gitTttUtRepo                = "${ispwStream}_${ispwApplication}_Unit_Tests.git"
        this.gitTttFtRepo                = "${ispwStream}_${ispwApplication}_Functional_Tests.git"

        this.cesTokenId                  = params.CES_Token
        this.hciConnId                   = params.HCI_Conn_ID
        this.hciTokenId                  = params.HCI_Token
        this.ccRepository                = params.CC_repository
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
        def tmpConfig = readConfigFile(pipelineConfigFile)

        sqScannerName   = tmpConfig.sqScannerName
        sqServerName    = tmpConfig.sqServerName
        sqServerUrl     = tmpConfig.sqServerUrl
        mfSourceFolder  = tmpConfig.mfSourceFolder
        xlrTemplate     = tmpConfig.xlrTemplate
        xlrUser         = tmpConfig.xlrUser
        tttFolder       = tmpConfig.tttFolder
        ispwUrl         = tmpConfig.ispwUrl
        ispwRuntime     = tmpConfig.ispwRuntime
    }

    /* Read configuration values from tttgit.config file */
    def setTttGitConfig()
    {
        def tmpConfig = readConfigFile(pipelineConfigFile)

        gitTargetBranch = tmpConfig.gitTargetBranch
        gitBranch       = tmpConfig.gitBranch
        gitFtBranch     = tmpConfig.gitFtBranch
        xaTesterUrl     = tmpConfig.xaTesterUrl
        xaTesterEnvId   = tmpConfig.xaTesterEnvId
    }

    /* Read list of email addresses from config file */
    def setMailConfig()
    {        
        def mailListMapTest = steps.readYaml(text: mailListLines.toString())
        
        steps.echo mailListMapTest

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
        def fileText    = steps.libraryResource filePath

        return steps.readYaml(text: fileText)
    }
}