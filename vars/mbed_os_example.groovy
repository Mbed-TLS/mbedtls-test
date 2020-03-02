def run_pr_job(example, is_production=true) {
    timestamps {
        environ.set_mbed_os_example_pr_environment(example, is_production)
        def jobs = gen_jobs.gen_all_example_jobs()
        jobs.failFast = false
        parallel jobs
    }
}
