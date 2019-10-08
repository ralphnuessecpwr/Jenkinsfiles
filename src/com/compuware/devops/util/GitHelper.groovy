package com.compuware.devops.util

/**
 Wrapper around the Git Plugin's Checkout Method
 @param URL - URL for the git server
 @param Branch - The branch that will be checked out of git
 @param Credentials - Jenkins credentials for logging into git
 @param Folder - Folder relative to the workspace that git will check out files into
*/
class GitHelper implements Serializable {

    def steps

    def gitUser
    def gitPassword

    GitHelper(steps) 
    {
        this.steps = steps
    }

    def initialize(String gitPassword, String gitUser, String gitUserName, String gitEmail)
    {
        this.gitUser            = gitUser
        this.gitPassword        = gitPassword

        def stdout              = steps.bat(script: "git config --global user.name ${gitUserName} \r\ngit config --global user.email ${gitEmail}")
    }

    def checkout(String gitUrl, String gitBranch, String gitCredentials, String tttFolder)
    {
        steps.checkout(
            changelog:  false, 
            poll:       false, 
            scm:        [
                        $class:                                 'GitSCM', 
                            branches:                           [[name: "*/${gitBranch}"]], 
                            doGenerateSubmoduleConfigurations:  false, 
                            extensions:                         [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${tttFolder}"]], 
                            submoduleCfg:                       [], 
                            userRemoteConfigs:                  [[credentialsId: "${gitCredentials}", name: 'origin', url: "${gitUrl}"]]
                        ]
        )
    }

    def checkoutPath(String gitUrl, String gitBranch, String path, String gitCredentials, String tttFolder)
    {
        steps.checkout(
        changelog: false, 
        poll: false, 
        scm: [
                $class: 'GitSCM', 
                branches: [[name: "*/${gitBranch}"]], 
                doGenerateSubmoduleConfigurations: false, 
                extensions: [
                    [
                    $class: 'SparseCheckoutPaths', 
                    sparseCheckoutPaths: [[path: "${path}/*"
                    ],
                    [
                    $class: 'RelativeTargetDirectory', 
                    relativeTargetDir: "${tttFolder}"
                    ]
                    ]
                ]], 
                submoduleCfg: [], 
                userRemoteConfigs: [[
                    credentialsId: "${gitCredentials}", 
                    url: "${gitUrl}"
                ]]
            ]
        )
    }

    def pushResults(String gitProject, String gitRepo, String tttFolder, String gitBranch, String jenkinsBuildNumber)
    {
        def message = '"Jenkins Build ' + jenkinsBuildNumber + '"'

        steps.dir(tttFolder)
        {
            steps.bat(returnStdout: true, script: 'git commit -a -m ' + message)
            steps.bat(returnStdout: true, script: "git push  https://${gitUser}:${gitPassword}@github.com/${gitProject}/${gitRepo} HEAD:${gitBranch} -f")
        }
    }
}