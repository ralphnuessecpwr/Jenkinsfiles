package com.compuware.devops.util

class ComponentStatus implements Serializable {
    String sourceStatus
    String utStatus
    String ftStatus
    String status
    String sonarGate
    String sonarProject

    def ComponentStatus()
    {
        sourceStatus = 'UNKNOWN'
        utStatus     = 'UNKNOWN'
        ftStatus     = 'UNKNOWN'
        sonarGate    = 'UNKNOWN'
        status       = 'UNKNOWN'
        sonarProject = 'UNKNOWN'
    }
}