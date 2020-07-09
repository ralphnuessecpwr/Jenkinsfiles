package com.compuware.devops.util

/**
 Static Class to contain different JCL Skeletons
*/
class JclSkeleton implements Serializable {

    def steps

    private String skeletonPath         = 'skels'               // Path containing JCL "skeletons" after downloading them from Git Hub Repository 'config\\skels'
    private String jobCardSkel          = 'JobCard.jcl'         // Skeleton for job cards
    private String cleanUpCcRepoSkel    = 'cleanUpCcRepo.skel'  // Skeleton for deleting the PDS after downloading copy books

    private String workspace

    String jobCardJcl
    String cleanUpCcRepoJclSkel
    String ispwApplication
    String ispwPathNum

    JclSkeleton(steps, String workspace, String ispwApplication, String ispwPathNum) 
    {
        this.steps              = steps
        this.workspace          = workspace
        this.ispwApplication    = ispwApplication
        this.ispwPathNum        = ispwPathNum
    }

    /* A Groovy idiosynchrasy prevents constructors to use methods, therefore class might require an additional "initialize" method to initialize the class */
    def initialize()
    {
        this.jobCardJcl                 = readSkelFile(jobCardSkel).join("\n")

        this.cleanUpCcRepoJclSkel       = readSkelFile(cleanUpCcRepoSkel).join("\n")
    }

    def String createCleanUpCcRepo(String systemName, String testId)
    {
        def cleanUpJcl  =   buildFinalJcl(jobCardJcl, 
                                cleanUpCcRepoJclSkel,
                                [
                                    [
                                        parmName:      "<cc_sysname>", 
                                        parmValue:     systemName
                                    ],
                                    [
                                        parmName:      "<cc_test_id>", 
                                        parmValue:     testId                                    
                                    ]
                                ]
                            )

        return cleanUpJcl
    }

    private String buildFinalJcl(jobCard, jclSkel, parametersMap)
    {
        String finalJcl

        finalJcl    = jobCard
        finalJcl    = finalJcl + "\n" + jclSkel

        parametersMap.each
        {
            finalJcl    = finalJcl.replace(it.parmName, it.parmValue)
        }

        return finalJcl
    }

    def readSkelFile(String fileName)
    {
        def jclStatements   = []
        def skelFilePath    = "${skeletonPath}\\${fileName}"
        def fileText        = steps.libraryResource skelFilePath
        def lines           = fileText.tokenize("\n")
        
        lines.each
        {
            jclStatements.add(it.toString())
        }

        return jclStatements
    }
}