def run_job() {
    timestamps {
        jobs = [:]
        if (BUILD_16_04_DOCKERFILE == 'true') {
            jobs += gen_jobs.gen_dockerfile_builder_job(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH, 'ubuntu-16.04', TAG_FOR_16_04_DOCKERFILE)
        }
        if (BUILD_18_04_DOCKERFILE == 'true') {
            jobs += gen_jobs.gen_dockerfile_builder_job(MBED_TLS_TEST_REPO, MBED_TLS_TEST_BRANCH, 'ubuntu-18.04', TAG_FOR_18_04_DOCKERFILE)
        }
        jobs.failFast = false
        parallel jobs
    }
}
