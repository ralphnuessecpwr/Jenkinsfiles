package com.compuware.devops.util

/**
 Object to store information about an ISPW program task
*/
class PipelineAsset implements Serializable 
{

    public String programName
    public String baseVersion
    public String targetVersion
    public String ispwTaskId

    PipelineAsset(steps) 
    {        
        this.programName    = ''
        this.baseVersion    = '0'
        this.targetVersion  = '0'
        this.ispwTaskId     = ''
    }

    def setProgramName(String name)
    {
        this.programName    = name
    }

    def setBaseVersion(String version)
    {
        this.baseVersion    = version
    }

    def setTargetVersion(String version)
    {
        this.taregtVersion  = version
    }

    def setIspwTaskId(String id)
    {
        this.ispwTaskId    = id
    }

}