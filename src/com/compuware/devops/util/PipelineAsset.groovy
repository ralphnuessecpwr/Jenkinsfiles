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
}