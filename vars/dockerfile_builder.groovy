def run_job() {
    timestamps {
        jobs = [:]
        if (ENABLE_16_04_DOCKERFILE == 'true') {
            switch (ACTION_FOR_16_04_DOCKERFILE) {
                case 'build':
                    jobs += gen_jobs.gen_dockerfile_builder_job(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH, 'ubuntu-16.04', TAG_FOR_16_04_DOCKERFILE)
                    break
                case 'publish':
                    jobs += gen_jobs.gen_docker_image_publisher_job('ubuntu-16.04', TAG_FOR_16_04_DOCKERFILE)
                    break
            }
        }
        if (ENABLE_18_04_DOCKERFILE == 'true') {
            switch (ACTION_FOR_18_04_DOCKERFILE) {
                case 'build':
                    jobs += gen_jobs.gen_dockerfile_builder_job(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH, 'ubuntu-18.04', TAG_FOR_18_04_DOCKERFILE)
                    break
                case 'publish':
                    jobs += gen_jobs.gen_docker_image_publisher_job('ubuntu-18.04', TAG_FOR_18_04_DOCKERFILE)
                    break
            }
        }
        jobs.failFast = false
        parallel jobs
    }
}
