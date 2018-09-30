package com.compuware.devops.util

/**
 Static Class to contain different JCL Skeletons
*/
class JclSkeleton implements Serializable {

    def steps

    String jobCardJcl
    String iebcopyCopyBooksJclSkel
    String cleanUpDatasetJclSkel
    String ispwApplication
    String ispwPathNum

    JclSkeleton(steps, String ispwApplication, String ispwPathNum) 
    {
        this.steps              = steps
        this.ispwApplication    = ispwApplication
        this.ispwPathNum        = ispwPathNum
    }

    def initialize(String workspace)
    {
        this.jobCardJcl                 = buildJobCard      (workspace)
        this.iebcopyCopyBooksJclSkel    = buildIebcopySkel  (workspace)
        this.cleanUpDatasetJclSkel      = buildDeleteSkel   (workspace)
    }

    def String buildJobCard(String workspace)
    {
        def skelFilePath    = "${workspace}\\config\\skels\\JobCard.jcl"
        def jclStatements   = []

        File skelFile = new File(skelFilePath)

        if(!skelFile.exists())
        {
            error "Skeleton not found for Job Card! \n Will abort Pipeline."
        }

        def lines           = skelFile.readLines()

        lines.each
        {
            jclStatements.add(it.toString())
        }

        return jclStatements.join("\n")
    }

    def String buildIebcopySkel(String workspace)
    {

        def skelFilePath        = "${workspace}\\config\\skels\\iebcopy.skel"
        def jclStatements       = []        
        def inputDdStatements   = []        
        def copyDdStatements    = []

        File skelFile1          = new File(skelFilePath)

        if(!skelFile1.exists())
        {
            error "Skeleton not found for IEBCOPY Skeleton! \n Will abort Pipeline."
        }

        def lines                   = skelFile1.readLines()

        lines.each
        {
            jclStatements.add(it.toString())
        }

        skelFilePath        = "${workspace}\\config\\skels\\iebcopyInDd.skel"

        File skelFile2      = new File(skelFilePath)

        if(!skelFile2.exists())
        {
            error "Skeleton not found for IEBCOPY Input DD Skeleton! \n Will abort Pipeline."
        }

        lines               = skelFile2.readLines()

        lines.each
        {
            inputDdStatements.add(it.toString())
        }        

        for(int i=0; i < inputDdStatements.size(); i++)
        {                        
            copyDdStatements.add ("       INDD=IN${i+1}")
        }

        def jclSkel         = jclStatements.join("\n")
        def inputDdJcl      = inputDdStatements.join("\n")
        def inputCopyJcl    = copyDdStatements.join("\n")

        jclSkel             = jclSkel.replace("<source_copy_pds_list>", inputDdJcl)
        jclSkel             = jclSkel.replace("<source_input_dd_list>", inputCopyJcl)
        jclSkel             = jclSkel.replace("<ispw_application>", ispwApplication)
        jclSkel             = jclSkel.replace("<ispw_path>", ispwPathNum)

        return jclSkel

    }

    def String buildDeleteSkel(workspace)
    {

        def skelFilePath    = "${workspace}\\config\\deleteDs.skel"
        def jclStatements   = []        

        File skelFile       = new File(skelFilePath)

        if(!skelFile.exists())
        {
            error "Skeleton not found for DELETE Skeleton! \n Will abort Pipeline."
        }

        def lines = skelFile.readLines()

        lines.each
        {
            jclStatements.add(it)
        }
        
        return jclStatements.join("\n")

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
}