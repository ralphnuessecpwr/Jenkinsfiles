package com.compuware.devops.util

/* 
    Pipeline execution specific and server specific parameters which are use throughout the pipeline
*/
class PipelineConfig implements Serializable
{
    def steps
    def mailListLines
    def mailListMap = [:]

    private String configPath           = 'pipeline'            // Path containing config files after downloading them from Git Hub Repository 'config\\pipeline'
    private String pipelineConfigFile   = 'pipeline.yml'        // Config file containing pipeline configuration

    private String workspace

    private params

    public ces  
    public hci  
    public ispw 
    public ttt  
    public git  
    public sonar     
    public xlr  
    public mail = [:]

    def PipelineConfig(steps, workspace, params, mailListLines)
    {
        this.steps              = steps
        this.workspace          = workspace
        this.mailListLines      = mailListLines
        this.params             = params
    }

    /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
    def initialize()
    {
        setServerConfig()

        setMailConfig()    
    }

    /* Read configuration values from pipeline.config file */
    private setServerConfig()
    {
        def configFilePath      = "${configPath}/${pipelineConfigFile}"
        def configFileText      = steps.libraryResource configFilePath
        def tmpConfig           = steps.readYaml(text: configFileText)

        this.ces                        = tmpConfig.ces
        this.hci                        = tmpConfig.hci
        this.ispw                       = tmpConfig.ispw
        this.ttt                        = tmpConfig.ttt
        this.git                        = tmpConfig.git
        this.sonar                      = tmpConfig.sonar      
        this.xlr                        = tmpConfig.xlr

        this.ispw.stream                = params.ISPW_Stream
        this.ispw.application           = params.ISPW_Application
        this.ispw.release               = params.ISPW_Release
        this.ispw.assignment            = params.ISPW_Assignment
        this.ispw.container             = params.ISPW_Set_Id
        this.ispw.containerType         = params.ISPW_Container_Type
        this.ispw.owner                 = params.ISPW_Owner        
        this.ispw.srcLevel              = params.ISPW_Src_Level

        this.ispw.applicationPathNum    = ispw.srcLevel.charAt(ispw.srcLevel.length() - 1)
        this.ispw.targetLevel           = "QA" + ispw.applicationPathNum
        this.ttt.runnerJcl              = "Runner_PATH" + ispw.applicationPathNum + ".jcl"

        this.git.url                    = "${this.git.server}/${this.git.project}"
        this.git.tttRepo                = "${this.ispw.stream}_${this.ispw.application}_Unit_Tests.git"
        this.git.tttUtRepo              = "${this.ispw.stream}_${this.ispw.application}_Unit_Tests.git"
        this.git.tttFtRepo              = "${this.ispw.stream}_${this.ispw.application}_Functional_Tests.git"

    }

    /* Read list of email addresses from config file */
    private setMailConfig()
    {        
        def lineToken
        def tsoUser
        def emailAddress

        mailListLines.each
        {
            lineToken       = it.toString().tokenize(":")
            tsoUser         = lineToken.get(0).toString()
            emailAddress    = lineToken.get(1).toString().trim()

            this.mailListMap."${tsoUser}" = "${emailAddress}"
        }

        this.mail.recipient = mailListMap[(ispw.owner.toUpperCase())]
    }
}