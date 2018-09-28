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

    JclSkeleton(steps, String ispwApplication, String ispwPathNum, String workspace) 
    {
        this.steps              = steps
        this.ispwApplication    = ispwApplication
        this.ispwPathNum        = ispwPathNum

        def lineToken
        def parmName
        def parmValue


        def skelFilePath        = "${workspace}\\config\\JobCard.jcl"

        File skelFile = new File(skelFilePath)

        if(!skelFile.exists())
        {
            error "Skeleton not found for Job Card! \n Will abort Pipeline."
        }

        def lines           = skelFile.readLines()
        def jclStatements   = []

        lines.each
        {
            jclStatements.add(it.toString())
        }

        this.jobCardJcl     = jclStatements.join("\n")


        skelFilePath        = "${workspace}\\config\\iebcopy.skel"

        File skelFile = new File(skelFilePath)

        if(!skelFile.exists())
        {
            error "Skeleton not found for IEBCOPY Skeleton! \n Will abort Pipeline."
        }

        lines                   = skelFile.readLines()
        jclStatements           = []

        lines.each
        {
            jclStatements.add(it.toString())
        }

        skelFilePath        = "${workspace}\\config\\iebcopyInDd.skel"

        File skelFile = new File(skelFilePath)

        if(!skelFile.exists())
        {
            error "Skeleton not found for IEBCOPY Input DD Skeleton! \n Will abort Pipeline."
        }

        lines                   = skelFile.readLines()
        def inputDdStatements   = []        

        lines.each
        {
            inputDdStatements.add(it.toString())
        }

        def copyDdStatements    = []

        for(int i=0; i <= inputDdStatements.size(); i++)
        {                        
            copyDdStatements.add ("       INDD=IN${i+1}")
        }

        this.iebcopyCopyBooksJclSkel    = jclStatements.join("\n")

        def inputDdJcl                  = inputDdStatements.join("\n")
        def inputCopyJcl                = copyDdStatements.join("\n")

        iebcopyCopyBooksJclSkel         = iebcopyCopyBooksJclSkel.replace("<source_copy_pds_list>", inputDdJcl)
        iebcopyCopyBooksJclSkel         = iebcopyCopyBooksJclSkel.replace("<source_input_dd_list>", inputCopyJcl)
        iebcopyCopyBooksJclSkel         = iebcopyCopyBooksJclSkel.replace("<ispw_application>", ispwApplication)
        iebcopyCopyBooksJclSkel         = iebcopyCopyBooksJclSkel.replace("<ispw_path>", ispwPathNum)


        skelFilePath        = "${workspace}\\config\\deleteDs.skel"

        File skelFile = new File(skelFilePath)

        if(!skelFile.exists())
        {
            error "Skeleton not found for DELETE Skeleton! \n Will abort Pipeline."
        }

        lines = skelFile.readLines()

        jclStatements   = []

        lines.each
        {
            jclStatements.add(it)
        }

        this.cleanUpDatasetJclSkel = jclStatements.join("\n")

    }

    def String createIebcopyCopyBooksJcl(String targetDsn, List copyMembers)
    {

        def iebcopyCopyBooksJcl = jobCardJcl

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