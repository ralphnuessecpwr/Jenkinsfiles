package com.compuware.devops.util

/**
 Object to store information about an ISPW program task
*/
class PipelineAsset {

    def steps
    public String programName
    public String baseVersion
    public String targetVersion
    public String ispwTaskId

    PipelineAsset(steps) 
    {
        this.steps          = steps
        this.programName    = ''
        this.baseVersion    = '0'
        this.targetVersion  = '0'
        this.ispwTaskId     = ''
    }

    def setProgramName(String name)
    {
        steps.echo "Set Name: " + name
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
        steps.echo "Set taskId: " + id
        this.ispwTaskId    = id
    }

}