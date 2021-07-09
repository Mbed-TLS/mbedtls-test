def run_job() {
    timestamps {
        jobs = [:]
        if (ENABLE_16_04_DOCKERFILE == 'true') {
            if (ACTION_FOR_16_04_DOCKERFILE == 'build') {
                jobs += gen_jobs.gen_dockerfile_builder_job(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH, 'ubuntu-16.04', TAG_FOR_16_04_DOCKERFILE)
            }
        }
        if (ENABLE_18_04_DOCKERFILE == 'true') {
            if (ACTION_FOR_18_04_DOCKERFILE == 'build') {
                jobs += gen_jobs.gen_dockerfile_builder_job(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH, 'ubuntu-18.04', TAG_FOR_18_04_DOCKERFILE)
            }
        }
        jobs.failFast = false
        parallel jobs
    }
}
