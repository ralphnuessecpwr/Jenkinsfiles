package com.compuware.devops.util

/**
 Static Class to contain different JCL Skeletons
*/
class JclSkeleton implements Serializable {

    String jobCardJcl
    String iebcopyCopyBooksJcl
    String cleanUpDatasetJcl

    JclSkeleton() 
    {

        def jclStatements = []

        jclStatements.add("//HDDRXM0X JOB ('EUDD,INTL'),'NUESSE',NOTIFY=&SYSUID,")
        jclStatements.add("//             MSGLEVEL=(1,1),MSGCLASS=X,CLASS=A,REGION=0M")

        this.jobCardJcl = jclStatements.join("\n")

        jclStatements = []

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

        this.iebcopyCopyBooksJclSkel = jclStatements.join("\n")

        jclStatements = []

        jclStatements.add("//CLEAN   EXEC PGM=IEFBR14")
        jclStatements.add("//DELETE   DD DISP=(SHR,DELETE,DELETE),DSN=<clean_dsn>")

        this.cleanUpDatasetJcl = jclStatements.join("\n")

    }
               
    def String createCopyBookCopyJcl(String targetDsn, List copyMembers, String ispwApplication, String ispwPathNum)
    {

        def iebcopyCopyBooksJcl = jobCardJcl

        def inputDdStatements   = []
        def copyDdStatements    = []
        def selectStatements    = []

        for(int i=0; i < 2; i++)
        {            
            inputDdStatements.add("//IN${i}       DD DISP=SHR,DSN=SALESSUP.${ispwApplication}.QA{$ispwPathNum}")
            copyDdStatements.add ("       INDD=IN${i}")
        }

        copyMembers.each {
            selectStatements.add("  SELECT MEMBER=${it}")
        }

        def inputDdJcl          = inputDdStatements.join("\n")
        def inputCopyJcl        = copyDdStatements.join("\n")
        def selectJcl           = selectStatements.join("\n")  

        iebcopyCopyBooksJcl + "\n" + iebcopyCopyBooksJclSkel
        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl.replace("<source_copy_pds_list>", inputDdJcl)
        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl.replace("<target_dsn>", targetDsn)
        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl.replace("<source_input_dd_list>", inputCopyJcl)
        iebcopyCopyBooksJcl = iebcopyCopyBooksJcl.replace("<select_list>", selectJcl)

        return iebcopyCopyBooksJcl

    }
}