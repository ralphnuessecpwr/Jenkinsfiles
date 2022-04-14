def call(execParms, pipelineConfig, ispwCurrentLevel, cesUrl) {

    stage("Start"){
        echo "COBOL Pipeline"
        echo execParms.toString()
        echo pipelineConfig.toString()
        echo ispwCurrentLevel
    }
}