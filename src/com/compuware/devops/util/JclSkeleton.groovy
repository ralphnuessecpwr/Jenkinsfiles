package com.compuware.devops.util

/**
 Static Class to contain different JCL Skeletons
*/
class JclSkeleton implements Serializable {

    String jobCardJcl
    String copyCopyBooksJcl
    String cleanUpDatasetJcl

    JclSkeleton() 
    {

        jclStatements = []

        jclStatements.add("//HDDRXM0X JOB ('EUDD,INTL'),'NUESSE',NOTIFY=&SYSUID,")
        jclStatements.add("//             MSGLEVEL=(1,1),MSGCLASS=X,CLASS=A,REGION=0M")

        this.jobCardJcl = jclStatements.join("\n")

        jclStatements.add("//COPY    EXEC PGM=IEBCOPY")
        jclStatements.add("//SYSPRINT DD SYSOUT=*")
        jclStatements.add("//SYSUT3   DD UNIT=SYSDA,SPACE=(TRK,(10,10))")
        jclStatements.add("//SYSUT4   DD UNIT=SYSDA,SPACE=(TRK,(10,10))")
        jclStatements.add("//IN1      DD DISP=SHR,DSN=SALESSUP.RXN3.DEV1.CPY")
        jclStatements.add("//IN2      DD DISP=SHR,DSN=SALESSUP.RXN3.QA1.CPY")
        jclStatements.add("//IN3      DD DISP=SHR,DSN=SALESSUP.RXN3.STG.CPY")
        jclStatements.add("//IN4      DD DISP=SHR,DSN=SALESSUP.RXN3.PRD.CPY")
        jclStatements.add("//OUT      DD DISP=(,CATLG,DELETE),")
        jclStatements.add("//            DSN=<target_dsn>,")
        jclStatements.add("//            UNIT=SYSDA,")
        jclStatements.add("//            SPACE=(TRK,(10,20,130)),")
        jclStatements.add("//            DCB=(RECFM=FB,LRECL=80)")
        jclStatements.add("//SYSIN DD *")
        jclStatements.add("  COPY OUTDD=OUT")
        jclStatements.add("       INDD=IN<source_dd_num>")

        this.copyCopyBooksJcl = jclStatements.join("\n")

        jclStatements = []

        jclStatements.add("////CLEAN   EXEC PGM=IEFBR14")
        jclStatements.add("//DELETE   DD DISP=(SHR,DELETE,DELETE),DSN=<clean_dsn>")

        this.cleanUpDatasetJcl = jclStatements.join("\n")

    }

    def createCopyBookCopyJcl(String targetDsn, List copyMembers, String gitCredentials, String tttFolder)
    {

    }
}