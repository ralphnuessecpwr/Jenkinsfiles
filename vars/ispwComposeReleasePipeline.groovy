import groovy.json.JsonSlurper
import com.compuware.devops.util.*

/**
 Helper Methods for the Pipeline Script
*/
PipelineConfig          pConfig         // Pipeline configuration parameters
IspwHelper              ispwHelper      // Helper class for interacting with ISPW
XlrHelper               xlrHelper       // Helper class for interacting with XLRelease
IspwReleaseConfigurator releaseConfigurator

String                  cesToken                // Clear text token from CES
String                  mailMessageExtension

private initialize(pipelineParams)
{
    // Clean out any previously downloaded source
    dir(".\\") 
    {
        deleteDir()
    }

    /* Read list of mailaddresses from "private" Config File */
    /* The configFileProvider creates a temporary file on disk and returns its path as variable */
    def mailListlines

    configFileProvider(
        [
            configFile(
                fileId: 'MailList', 
                variable: 'mailListFilePath'
            )
        ]
    ) 
    {
        File mailConfigFile = new File(mailListFilePath)

        if(!mailConfigFile.exists())
        {
            error "File - ${mailListFilePath} - not found! \n Aborting Pipeline"
        }

        mailListlines = mailConfigFile.readLines()
    }

    pipelineParams.ISPW_Assignment  = ''
    pipelineParams.ISPW_Set_Id      = ''
    pipelineParams.ISPW_Owner       = pipelineParams.User_Id
    pipelineParams.ISPW_Src_Level   = 'DEV1'

    // Instantiate and initialize Pipeline Configuration settings
    pConfig = new PipelineConfig(
        steps, 
        workspace,
        pipelineParams,
        mailListlines
    )

    pConfig.initialize()                                            

    // Use Jenkins Credentials Provider plugin to retrieve CES token in clear text from the Jenkins token for the CES token
    // The clear text token is needed for native http REST requests against the ISPW API
    withCredentials(
        [string(credentialsId: "${pConfig.ces.jenkinsToken}", variable: 'cesTokenTemp')]
    ) 
    {
        cesToken = cesTokenTemp
    }

    // Instanatiate and initialize the ISPW Helper
    ispwHelper  = new IspwHelper(
        steps, 
        pConfig
    )

    releaseConfigurator = new IspwReleaseConfigurator(
        steps,
        pConfig,
        pipelineParams.ISPW_Assignment_List
    )    

    mailMessageExtension = ''
}


/**
Call method to execute the pipeline from a shared library
@param pipelineParams - Map of paramter/value pairs
*/
def call(Map pipelineParams)
{
    node
    {
        stage("Initialization")
        {
            initialize(pipelineParams) 
            echo "Determined"
            echo "Application   :" + pConfig.ispw.application
            echo "Release       :" + pConfig.ispw.release
        }
                
        /* Download all sources that are part of the container  */
        stage("Perform Action")
        {
            switch(pipelineParams.Release_Action) 
            {
                case "create Release":
                    mailMessageExtension = mailMessageExtension + releaseConfigurator.createRelease()
                    echo "After create Release"
                    echo mailMessageExtension
                    mailMessageExtension = mailMessageExtension + releaseConfigurator.addAssignments()
                    echo "After add Assignments"
                    echo mailMessageExtension
                break

                case "add Assignments":
                    mailMessageExtension = mailMessageExtension + releaseConfigurator.addAssignments()
                    echo "After add Assignments"
                    echo mailMessageExtension
                break

                case "remove Assignments":
                    mailMessageExtension = mailMessageExtension + releaseConfigurator.removeAssignments()
                    echo "After remove Assignments"
                    echo mailMessageExtension
                break

                case "trigger Release":

                    def failAssignmentList = releaseConfigurator.checkReleaseReady()

                    if(failAssignmentList.size() == 0)
                    {
                        // Instantiate and initialize the XLR Helper
                        xlrHelper   = new XlrHelper(steps, pConfig)

                        xlrHelper.triggerRelease()

                        mailMessageExtension = mailMessageExtension + "Triggered XL Release for " + pConfig.ispw.release + ".\n"
                    }
                    else
                    {
                        mailMessageExtension = mailMessageExtension + "Some assignments for Release " + pConfig.ispw.release + "still contain tasks in development.\n" +
                            "The release cannot be triggered. Either remove those tasks from the assignments or remove the assignments from the release:\n\n"

                        failAssignmentList.each
                        {
                            mailMessageExtension = mailMessageExtension + it + "\n"
                        }
                    }

                break

                default:
                    echo "Wrong Action Code"
                break
            }
        }

        stage("Send Notifications")
        {
            emailext subject:       'Performed Action ' + pipelineParams.Release_Action + ' on Release ' + pConfig.ispw.release,
            body:       mailMessageExtension,
            replyTo:    '$DEFAULT_REPLYTO',
            to:         "${pConfig.mail.recipient}"
        }
    }
}