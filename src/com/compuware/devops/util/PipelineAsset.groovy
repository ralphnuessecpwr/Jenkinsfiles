package com.compuware.devops.util

/**
 Object to store information about an ISPW program task
*/
class PipelineAsset
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

    public void setProgramName(String name)
    {
        this.programName    = name
    }

    public void setBaseVersion(String version)
    {
        this.baseVersion    = version
    }

    public void setTargetVersion(String version)
    {
        this.taregtVersion  = version
    }

    public void setIspwTaskId(String id)
    {
        this.ispwTaskId    = id
    }

}