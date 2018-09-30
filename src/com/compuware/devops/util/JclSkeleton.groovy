package com.compuware.devops.util

/**
 Static Class to contain different JCL Skeletons
*/
class JclSkeleton implements Serializable {

    def steps

    private String skeletonPath     = 'config\\skels'
    private String jobCardSkel      = 'JobCard.jcl'
    private String iebcopySkel      = 'iebcopy.skel'
    private String iebcopyInDdSkel  = 'iebcopyInDd.skel'
    private String deleteDsSkel     = 'deleteDs.skel'

    private String workspace

    String jobCardJcl
    String iebcopyCopyBooksJclSkel
    String cleanUpDatasetJclSkel
    String ispwApplication
    String ispwPathNum

    JclSkeleton(steps, String workspace, String ispwApplication, String ispwPathNum) 
    {
        this.steps              = steps
        this.workspace          = workspace
        this.ispwApplication    = ispwApplication
        this.ispwPathNum        = ispwPathNum
    }

    def initialize()
    {
        this.jobCardJcl                 = readSkelFile(jobCardSkel).join("\n")
        steps.echo "Read JobCard \n" + jobCardJcl

        this.cleanUpDatasetJclSkel      = readSkelFile(deleteDsSkel).join("\n")
        steps.echo "Read Delete JCL \n" + cleanUpDatasetJclSkel

        this.iebcopyCopyBooksJclSkel    = buildIebcopySkel()
        steps.echo "Read IEBCOPY JCL \n" + iebcopyCopyBooksJclSkel
    }

    def String buildIebcopySkel()
    {

        def jclSkel                 = readSkelFile(iebcopySkel).join("\n")
        
        def tempInputDdStatements   = readSkelFile(iebcopyInDdSkel)
        def copyDdStatements        = []

        for(int i=0; i < tempInputDdStatements.size(); i++)
        {                        
            copyDdStatements.add ("       INDD=IN${i+1}")
        }

        def inputDdJcl      = tempInputDdStatements.join("\n")
        def inputCopyJcl    = copyDdStatements.join("\n")

        jclSkel             = jclSkel.replace("<source_copy_pds_list>", inputDdJcl)
        jclSkel             = jclSkel.replace("<source_input_dd_list>", inputCopyJcl)
        jclSkel             = jclSkel.replace("<ispw_application>", ispwApplication)
        jclSkel             = jclSkel.replace("<ispw_path>", ispwPathNum)

        return jclSkel

    }

    def String buildDeleteSkel()
    {

        def skelFilePath    = "${workspace}\\${skeletonPath}\\${deleteDsSkel}"
        def jclStatements   = []        

        File skelFile       = new File(skelFilePath)

        if(!skelFile.exists())
        {
            steps.error "Skeleton not found for DELETE Skeleton! \n Will abort Pipeline."
        }

        def lines = skelFile.readLines()

        lines.each
        {
            jclStatements.add(it)
        }
        
        return jclStatements

    }

    def String createIebcopyCopyBooksJcl(String targetDsn, List copyMembers)
    {

        def iebcopyCopyBooksJcl = this.jobCardJcl
        def selectStatements    = []

        copyMembers.each {
            selectStatements.add("  SELECT MEMBER=${it}")
        }

        def selectJcl           = selectStatements.join("\n")  

        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl + "\n" + iebcopyCopyBooksJclSkel
        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl.replace("<target_dsn>", targetDsn)
        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl.replace("<select_list>",selectJcl)

        return iebcopyCopyBooksJcl

    }

    def String createDeleteTempDsn(String targetDsn)
    {
        def deleteJcl   = jobCardJcl

        deleteJcl       = deleteJcl + "\n" + cleanUpDatasetJclSkel
        deleteJcl       = deleteJcl.replace("<clean_dsn>", targetDsn)

        return deleteJcl
    }

    def readSkelFile(String fileName)
    {
        def jclStatements   = []
        
        def skelFilePath    = "${workspace}\\${skeletonPath}\\${fileName}"

        File skelFile = new File(skelFilePath)

        if(!skelFile.exists())
        {
            steps.error "Skeleton - ${skelFilePath} - not found! \n Will abort Pipeline."
        }

        def lines           = skelFile.readLines()

        lines.each
        {
            jclStatements.add(it.toString())
        }

        return jclStatements
    }
}