package com.compuware.devops.util

/**
 Wrapper around the XLR Plugin
*/
class XlrHelper implements Serializable {

    def steps
    def pConfig

    XlrHelper(steps, pConfig) 
    {
        this.steps      = steps
        this.pConfig    = pConfig
    }

    def triggerRelease()
    {
        // Trigger XL Release Jenkins Plugin to kickoff a Release
        steps.xlrCreateRelease(
            releaseTitle:       'A Release for $BUILD_TAG',
            serverCredentials:  "${pConfig.xlr.user}",
            startRelease:       true,
            template:           "${pConfig.xlr.template}",
            variables:          [
                                    [propertyName:  'ISPW_Dev_level',   propertyValue: "${pConfig.ispw.targetLevel}"], // Level in ISPW that the Code resides currently
                                    [propertyName:  'ISPW_RELEASE_ID',  propertyValue: "${pConfig.ispw.release}"],     // ISPW Release value from the ISPW Webhook
                                    [propertyName:  'CES_Token',        propertyValue: "${pConfig.ces.jenkinsToken}"]
                                ]
        )

    }
}