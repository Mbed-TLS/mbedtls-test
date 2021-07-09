def run_job() {
    timestamps {
        stage('build-dockerfiles') {
            node('dockerfile-builder') {
                dir('src') {
                    deleteDir()
                    checkout_repo.checkout_parametrized_repo(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH)
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
