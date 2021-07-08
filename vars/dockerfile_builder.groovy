def run_job() {
    timestamps {
        stage('build-dockerfiles') {
            node('dockerfile-builder') {
                dir('src') {
                    deleteDir()
                    checkout([
                        scm: [
                            $class: 'GitSCM',
                            userRemoteConfigs: [
                                [url: MBED_TLS_TEST_REPO, credentialsId: env.GIT_CREDENTIALS_ID]
                            ],
                            branches: [[name: MBED_TLS_TEST_BRANCH]],
                            extensions: [
                                [$class: 'CloneOption', timeout: 60],
                                [$class: 'SubmoduleOption', recursiveSubmodules: true],
                                [$class: 'LocalBranch', localBranch: MBED_TLS_TEST_BRANCH],
                            ],
                        ]
                    ])
                    dir('dev_envs') {
                        dir('docker_files') {
                            if (BUILD_16_04_DOCKERFILE == "true") {
                                dir('ubuntu-16.04') {
                                    sh """\
\$(aws ecr get-login) && docker pull 666618195821.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls:ubuntu-16.04
\$(aws ecr get-login) && docker build -t jenkins-mbedtls:${TAG_FOR_16_04_DOCKERFILE} .
docker tag jenkins-mbedtls:${TAG_FOR_16_04_DOCKERFILE} 666618195821.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls:${TAG_FOR_16_04_DOCKERFILE}
\$(aws ecr get-login) && docker push 666618195821.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls:${TAG_FOR_16_04_DOCKERFILE}
"""
                                }
                            }
                            if (BUILD_18_04_DOCKERFILE == "true") {
                                dir('ubuntu-18.04') {
                                    sh """\
\$(aws ecr get-login) && docker pull 666618195821.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls:ubuntu-18.04
\$(aws ecr get-login) && docker build -t jenkins-mbedtls:${TAG_FOR_18_04_DOCKERFILE} .
docker tag jenkins-mbedtls:${TAG_FOR_18_04_DOCKERFILE} 666618195821.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls:${TAG_FOR_18_04_DOCKERFILE}
\$(aws ecr get-login) && docker push 666618195821.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls:${TAG_FOR_18_04_DOCKERFILE}
"""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
