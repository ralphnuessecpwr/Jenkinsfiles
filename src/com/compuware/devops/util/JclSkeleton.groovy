package com.compuware.devops.util

/**
 Static Class to contain different JCL Skeletons
*/
class JclSkeleton implements Serializable {

    def steps

    String jobCardJcl
    String iebcopyCopyBooksJclSkel
    String cleanUpDatasetJclSkel

    JclSkeleton(steps, String ispwApplication, String ispwPathNum) 
    {
        this.steps = steps

        this.jobCardJcl                 = initJobCardJcl()
        this.iebcopyCopyBooksJclSkel    = initIebcopyJclSkel(ispwApplication, ispwPathNum)
        this.cleanUpDatasetJclSkel      = initCleanupJclSkel()

    }

    def String initCleanupJclSkel()
    {
        def jclSkel         = ''
        def jclStatements   = []

        jclStatements.add("//CLEAN   EXEC PGM=IEFBR14")
        jclStatements.add("//DELETE   DD DISP=(SHR,DELETE,DELETE),DSN=<clean_dsn>")

        jclSkel = jclStatements.join("\n")

        return jclSkel
    }

    def String initJobCardJcl()
    {
        def jclStatements = []

        jclStatements.add("//HDDRXM0J JOB ('EUDD,INTL'),'NUESSE',NOTIFY=&SYSUID,")
        jclStatements.add("//             MSGLEVEL=(1,1),MSGCLASS=X,CLASS=A,REGION=0M")

        return jclStatements.join("\n")
    }

    def String initIebcopyJclSkel(String ispwApplication, String ispwPathNum)
    {
        def jclSkel             = ''
        def jclStatements       = []
        def inputDdStatements   = []
        def copyDdStatements    = []

        jclStatements.add("//COPY    EXEC PGM=IEBCOPY")
        jclStatements.add("//SYSPRINT DD SYSOUT=*")
        jclStatements.add("//SYSUT3   DD UNIT=SYSDA,SPACE=(TRK,(10,10))")
        jclStatements.add("//SYSUT4   DD UNIT=SYSDA,SPACE=(TRK,(10,10))")
        jclStatements.add("<source_copy_pds_list>")
        jclStatements.add("//OUT      DD DISP=(,CATLG,DELETE),")
        jclStatements.add("//            DSN=<target_dsn>,")
        jclStatements.add("//            UNIT=SYSDA,")
        jclStatements.add("//            SPACE=(TRK,(10,20,130)),")
        jclStatements.add("//            DCB=(RECFM=FB,LRECL=80)")
        jclStatements.add("//SYSIN DD *")
        jclStatements.add("  COPY OUTDD=OUT")
        jclStatements.add("<source_input_dd_list>")
        jclStatements.add("<select_list>")

        jclSkel = jclStatements.join("\n")

        inputDdStatements.add("//IN1      DD DISP=SHR,DSN=SALESSUP.${ispwApplication}.QA${ispwPathNum}.CPY")
        inputDdStatements.add("//IN2      DD DISP=SHR,DSN=SALESSUP.${ispwApplication}.STG.CPY")
        inputDdStatements.add("//IN3      DD DISP=SHR,DSN=SALESSUP.${ispwApplication}.PRD.CPY")

        for(int i=0; i <= 2; i++)
        {                        
            copyDdStatements.add ("       INDD=IN${i+1}")
        }

        def inputDdJcl          = inputDdStatements.join("\n")
        def inputCopyJcl        = copyDdStatements.join("\n")

        jclSkel = jclSkel.replace("<source_copy_pds_list>", inputDdJcl)
        jclSekl = jclSkel.replace("<source_input_dd_list>", inputCopyJcl)

        return jclSkel

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

        return iebcopyCopyBooksJcl

    }

    def String createDeleteTempDsn(String targetDsn)
    {
        def deleteJcl   = jobCardJcl

        deleteJcl       = deleteJcl.replace("<clean_dsn>", targetDsn)

        return deleteJcl
    }
}