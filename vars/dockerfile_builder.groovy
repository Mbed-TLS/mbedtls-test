def gen_job_for_action(action, platform) {
    switch (action) {
        case 'build':
            return gen_jobs.gen_dockerfile_builder_job(platform, true)
        case 'skip':
            return [:]
        default:
            throw new IllegalArgumentException(action)
    }
}

def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            def jobs = gen_job_for_action(DOCKER_IMAGE_16_04_ACTION, 'ubuntu-16.04')
            jobs += gen_job_for_action(DOCKER_IMAGE_18_04_ACTION, 'ubuntu-18.04')
            jobs += gen_job_for_action(DOCKER_IMAGE_20_04_ACTION, 'ubuntu-20.04')
            jobs += gen_job_for_action(DOCKER_IMAGE_22_04_ACTION, 'ubuntu-22.04')
            jobs.failFast = false
            parallel jobs
        }
    }
}
