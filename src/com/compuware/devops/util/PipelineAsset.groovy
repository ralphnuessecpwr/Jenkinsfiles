package com.compuware.devops.util

/**
 Object to store information about an ISPW program task
*/
class PipelineAsset {

    String programName
    String baseVersion
    String targetVersion
    String ispwTaskId

    PipelineAsset() 
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