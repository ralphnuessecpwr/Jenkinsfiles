package com.compuware.devops.util

/* 
    Pipeline execution specific and server specific parameters which are use throughout the pipeline
*/
class PipelineConfigNew implements Serializable
{
    def steps
    
    File gitConfigFile

    def mailListMap                 = ["HDDRXM0":"ralph.nuesse@compuware.com"]

/* Environment specific settings, which differ between Jenkins servers and applications, but not between runs */
    public String gitTargetBranch   = "CONS"
    public String gitBranch         = "master"
    /*
    public String sqScannerName     = "scanner" //"Scanner" //"scanner" 
    public String sqServerName      = "localhost"  //"CWCC" //"localhost"  
    public String sqServerUrl       = 'http://sonarqube.nasa.cpwr.corp:9000'
    public String mfSourceFolder    = "MF_Source"
    public String xlrTemplate       = "A Release from Jenkins" //"A Release from Jenkins - RNU" //"A Release from Jenkins"
    public String xlrUser           = "admin"    //"xebialabs" //"admin"                           
    public String tttFolder         = "tests"	
    public String ispwUrl           = "http://cwcc.compuware.com:2020"
    public String ispwRuntime       = "ispw"		 
    */
    
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

    def PipelineConfigNew(steps, params)
    {
        this.steps              = steps

        steps.echo"After steps"

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

        this.mailRecipient      = mailListMap[(ispwOwner.toUpperCase())]
    }

    def initialize(String workspace)
    {
        /* Get configuration files from github */
        steps.checkout(
            changelog: false, 
            poll: false, 
            scm: [
                    $class: 'GitSCM', 
                    branches: [[name: '*/Dev']], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [[
                        $class: 'SparseCheckoutPaths', 
                        sparseCheckoutPaths: [[path: 'config/*']]
                    ]], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[
                        credentialsId: '87763671-db9a-47e1-80e7-33c1aba803b1', 
                        url: 'https://github.com/ralphnuessecpwr/Jenkinsfiles.git'
                    ]]
                ]
        )

        /* Read Pipeline and environment specific parms */
        def filePath = "${workspace}\\config\\pipeline.config"

        File pipelineConfigFile = new File(filePath)

        if(!pipelineConfigFile.exists())
        {
            steps.error "Pipeline Configuration File not found! \n Aborting Pipeline"
        }

        def lineToken
        def parmName
        def parmValue
        def lines       = pipelineConfigFile.readLines()

        lines.each
        {
            lineToken   = it.toString().tokenize("=")
            parmName    = lineToken.get(0).toString()
            parmValue   = lineToken.get(1).toString()

            switch(parmValue)
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
}