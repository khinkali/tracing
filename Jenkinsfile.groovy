@Library('semantic_releasing') _

podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'maven', image: 'maven:3.5.2-jdk-8', command: 'cat', ttyEnabled: true)
],
        volumes: [
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
        ]) {

    node('mypod') {
        properties([
                buildDiscarder(
                        logRotator(artifactDaysToKeepStr: '',
                                artifactNumToKeepStr: '',
                                daysToKeepStr: '',
                                numToKeepStr: '30'
                        )
                ),
                pipelineTriggers([])
        ])

        stage('checkout & unit tests & deploy to nexus') {
            git url: 'https://github.com/khinkali/tracing'
            env.VERSION = semanticReleasing()
            currentBuild.displayName = env.VERSION

            container('maven') {
                sh "mvn versions:set -DnewVersion=${env.VERSION}"
            }
            sh "git config user.email \"jenkins@khinkali.ch\""
            sh "git config user.name \"Jenkins\""
            sh "git tag -a ${env.VERSION} -m \"${env.VERSION}\""
            withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/khinkali/tracing.git --tags"
            }
            withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'NEXUS_PASSWORD', usernameVariable: 'NEXUS_USERNAME')]) {
                container('maven') {
                    sh "mvn -s settings.xml clean deploy"
                }
            }
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
        }

    }
}