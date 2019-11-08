def run_pr_job(example) {
    timestamps {
        node {
            deleteDir()
            environ.set_mbed_os_example_pr_environment(example)
            def jobs = gen_jobs.gen_all_example_jobs()
            jobs.failFast = false
            parallel jobs
        }
    }
}
