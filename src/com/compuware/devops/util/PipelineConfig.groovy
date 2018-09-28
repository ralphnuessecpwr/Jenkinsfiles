package com.compuware.devops.util

/* 
    Pipeline execution specific and server specific parameters which are use throughout the pipeline
*/
class PipelineConfig implements Serializable
{
    def steps

    def mailListMap

/* Environment specific settings, which differ between Jenkins servers and applications, but not between runs */
    public String gitTargetBranch
    public String gitBranch      
    
    public String sqScannerName  
    public String sqServerName   
    public String sqServerUrl    
    public String mfSourceFolder 
    public String xlrTemplate    
    public String xlrUser        
    public String tttFolder      
    public String ispwUrl        
    public String ispwRuntime    

/* Runtime specific settings, which differ runs and get passed as parameters or determined during execution */
    public String ispwStream
    public String ispwApplication
    public String ispwRelease
    public String ispwContainer
    public String ispwContainerType
    public String ispwSrcLevel
    public String ispwTargetLevel
    public String ispwOwner         
    public String applicationPathNum

    public String gitProject        
    public String gitCredentials    
    public String gitUrl            
    public String gitTttRepo        

    public String cesTokenId        
    public String cesTokenClear     
    public String hciConnId         
    public String hciTokenId        
    public String ccRepository      

    public String tttJcl 
      
    public String mailRecipient 

    def PipelineConfig(steps, params)
    {
        this.steps              = steps

        this.ispwStream         = params.ISPW_Stream
        this.ispwApplication    = params.ISPW_Application
        this.ispwRelease        = params.ISPW_Release
        this.ispwContainer      = params.ISPW_Container
        this.ispwContainerType  = params.ISPW_Container_Type
        this.ispwOwner          = params.ISPW_Owner        
        this.ispwSrcLevel       = params.ISPW_Src_Level

        this.applicationPathNum = ispwSrcLevel.charAt(ispwSrcLevel.length() - 1)
        this.ispwTargetLevel    = "QA" + applicationPathNum
        this.tttJcl             = "Runner_PATH" + applicationPathNum + ".jcl"

        this.gitProject         = params.Git_Project
        this.gitCredentials     = params.Git_Credentials
        this.gitUrl             = "https://github.com/${gitProject}"
        this.gitTttRepo         = "${ispwStream}_${ispwApplication}_Unit_Tests.git"

        this.cesTokenId         = params.CES_Token       
        this.hciConnId          = params.HCI_Conn_ID
        this.hciTokenId         = params.HCI_Token
        this.ccRepository       = params.CC_repository
    }

    def initialize(String workspace)
    {
        def configGitBranch     = "Dev"
        def configGitProject    = "Jenkinsfiles"
        def configGitPath       = "config"

        GitHelper gitHelper     = new GitHelper(steps)

        gitHelper.checkoutPath(gitUrl, configGitBranch, configGitPath, gitCredentials, configGitProject)

        setServerConfig(workspace)
    
    }

    def setServerConfig(String workspace)
    {
        /* Read Pipeline and environment specific parms */
        def filePath = "${workspace}\\config\\pipeline.config"

        File configFile = new File(filePath)

        if(!configFile.exists())
        {
            steps.error "Pipeline Configuration File not found! \n Aborting Pipeline"
        }

        def lineToken
        def parmName
        def parmValue
        def lines       = configFile.readLines()

        lines.each
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
                    steps.echo "Found unknown Parameter " + parmName + " " + parmValue + "\nWill ignore and continue."
                    break;
            }
        }
    }

    def setTttGitConfig(String workspace)
    {
        /* Read Pipeline and environment specific parms */
        def filePath = "${workspace}\\config\\tttgit.config"

        File configFile = new File(filePath)

        if(!configFile.exists())
        {
            steps.error "TTT Git Configuration File not found! \n Aborting Pipeline"
        }

        def lineToken
        def parmName
        def parmValue
        def lines       = configFile.readLines()

        lines.each
        {
            lineToken   = it.toString().tokenize("=")
            parmName    = lineToken.get(0).toString()
            parmValue   = lineToken.get(1).toString().trim()

            switch(parmName)
            {
                case "TTT_GIT_TARGET_BRANCH":
                    gitTargetBranch   = parmValue
                    break;
                case "TTT_GIT_BRANCH": 
                    gitBranch    = parmValue
                    break;
                default:
                    steps.echo "Found unknown Parameter " + parmName + " " + parmValue + "\nWill ignore and continue."
                    break;
            }
        }
    }

    def setMailConfig(String workspace)
    {
        /* Read Pipeline and environment specific parms */
        def filePath = "${workspace}\\config\\mail.config"

        File configFile = new File(filePath)

        if(!configFile.exists())
        {
            steps.error "Mail Configuration File not found! \n Aborting Pipeline"
        }

        def lineToken
        def tsoUser
        def emailAddress
        def lines       = configFile.readLines()

        lines.each
        {
            lineToken       = it.toString().tokenize("=")
            tsoUser         = lineToken.get(0).toString()
            emailAddress    = lineToken.get(1).toString().trim()

            mailListMap.(tsoUser) = "${emailAddress}"
        }

        this.mailRecipient  = mailListMap[(ispwOwner.toUpperCase())]

    }

}