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
docker build -t $common.docker_repo:${TAG_FOR_16_04_DOCKERFILE} .
\$(aws ecr get-login) && docker push $common.docker_repo:${TAG_FOR_16_04_DOCKERFILE}
"""
                                }
                            }
                            if (BUILD_18_04_DOCKERFILE == "true") {
                                dir('ubuntu-18.04') {
                                    sh """\
docker build -t $common.docker_repo:${TAG_FOR_18_04_DOCKERFILE} .
\$(aws ecr get-login) && docker push $common.docker_repo:${TAG_FOR_18_04_DOCKERFILE}
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
