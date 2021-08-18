def gen_job_for_action(action, platform, tag) {
    switch (action) {
        case 'build':
            return gen_jobs.gen_dockerfile_builder_job(TEST_REPO, TEST_BRANCH, platform, tag)
        case 'publish':
            return gen_jobs.gen_docker_image_publisher_job(platform, tag)
    }
}

def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            def jobs = gen_job_for_action(DOCKER_IMAGE_16_04_ACTION, 'ubuntu-16.04', DOCKER_IMAGE_16_04_TAG)
            jobs += gen_job_for_action(DOCKER_IMAGE_18_04_ACTION, 'ubuntu-18.04', DOCKER_IMAGE_18_04_TAG)
            jobs.failFast = false
            parallel jobs
        }
    }
}
