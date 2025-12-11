def gen_job_for_action(action, platform) {
    switch (action) {
        case 'true':
            return gen_jobs.gen_dockerfile_builder_job(platform, true)
        default:
            return [:]
    }
}

def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            def jobs = gen_job_for_action(BUILD_UBUNTU_16_04_AMD64_DOCKER_IMAGE, 'ubuntu-16.04-amd64')
            jobs += gen_job_for_action(BUILD_UBUNTU_18_04_AMD64_DOCKER_IMAGE, 'ubuntu-18.04-amd64')
            jobs += gen_job_for_action(BUILD_UBUNTU_24_04_AMD64_DOCKER_IMAGE, 'ubuntu-24.04-amd64')
            jobs.failFast = false
            parallel jobs
        }
    }
}
