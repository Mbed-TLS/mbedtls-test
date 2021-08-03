def gen_job_for_action(action, platform, tag) {
    jobs = [:]
    switch (action) {
        case 'build':
            jobs += gen_jobs.gen_dockerfile_builder_job(TEST_REPO, TEST_BRANCH, platform, tag)
            break
        case 'publish':
            jobs += gen_jobs.gen_docker_image_publisher_job(platform, tag)
            break
    }
    return jobs
}

def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            jobs  = gen_job_for_action(DOCKER_IMAGE_16_04_ACTION, 'ubuntu-16.04', DOCKER_IMAGE_16_04_TAG)
            jobs += gen_job_for_action(DOCKER_IMAGE_18_04_ACTION, 'ubuntu-18.04', DOCKER_IMAGE_18_04_TAG)
            jobs.failFast = false
            parallel jobs
        }
    }
}
