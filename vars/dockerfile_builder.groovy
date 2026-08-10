def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            def jobs = [:]
            if (params.BUILD_UBUNTU_16_04_AMD64_DOCKER_IMAGE) {
                jobs += gen_jobs.gen_dockerfile_builder_job('ubuntu-16.04-amd64', true)
            }
            if (params.BUILD_UBUNTU_18_04_AMD64_DOCKER_IMAGE) {
                jobs += gen_jobs.gen_dockerfile_builder_job('ubuntu-18.04-amd64', true)
            }
            if (params.BUILD_UBUNTU_18_04_ARM64_DOCKER_IMAGE) {
                jobs += gen_jobs.gen_dockerfile_builder_job('ubuntu-18.04-arm64', true)
            }
            if (params.BUILD_UBUNTU_24_04_AMD64_DOCKER_IMAGE) {
                jobs += gen_jobs.gen_dockerfile_builder_job('ubuntu-24.04-amd64', true)
            }
            if (params.BUILD_UBUNTU_24_04_ARM64_DOCKER_IMAGE) {
                jobs += gen_jobs.gen_dockerfile_builder_job('ubuntu-24.04-arm64', true)
            }
            if (params.BUILD_ARM_COMPILERS_AMD64_DOCKER_IMAGE) {
                jobs += gen_jobs.gen_dockerfile_builder_job('arm-compilers-amd64', true)
            }
            jobs.failFast = false
            parallel jobs
        }
    }
}
