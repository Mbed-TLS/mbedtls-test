/* This runs the job using the main TLS development branch and a Mbed Crypto PR */
def run_tls_tests_with_crypto_pr(is_production) {
    env.REPO_TO_CHECKOUT = 'tls'
    if (is_production) {
        env.MBED_TLS_BRANCH = 'development'
        env.MBED_TLS_REPO = "git@github.com:ARMmbed/mbedtls.git"
    }
    common.get_all_sh_components(['ubuntu-16.04', 'ubuntu-18.04'])
    run_tls_tests('tls-')
}

def run_tls_tests(label_prefix='') {
    try {
        def jobs = [:]

        jobs = jobs + gen_jobs.gen_release_jobs(label_prefix, false)

        if (env.RUN_ABI_CHECK == "true") {
            jobs = jobs + gen_jobs.gen_abi_api_checking_job('ubuntu-16.04')
        }

        jobs.failFast = false
        parallel jobs
        if (env.BRANCH_NAME) {
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'All tests passed',
                         status: 'SUCCESS'
        }
    } catch (err) {
        echo "Caught: ${err}"
        currentBuild.result = 'FAILURE'
        if (env.BRANCH_NAME) {
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'Test failure',
                         status: 'FAILURE'
        }
    }
}

/* main job */
def run_pr_job(is_production=true) {
    timestamps {
        /* During the nightly branch indexing, if a target branch has been
         * updated, new merge jobs are triggered for each PR to that branch.
         * If a PR hasn't been updated in over a month, don't run the merge
         * job for that PR.
         */
        if (env.BRANCH_NAME ==~ /PR-\d+-merge/ &&
            currentBuild.rawBuild.getCauses()[0].toString().contains('BranchIndexingCause'))
        {
            upd_timestamp_ms = pullRequest.updatedAt.getTime()
            now_timestamp_ms = currentBuild.startTimeInMillis
            long month_ms = 30L * 24L * 60L * 60L * 1000L
            if (now_timestamp_ms - upd_timestamp_ms > month_ms) {
                error('Not running: PR has not been updated in over a month.')
            }
        }

        if (env.BRANCH_NAME) {
            githubNotify context: "${env.BRANCH_NAME} Pre Test Checks",
                         description: 'Checking if all PR tests can be run',
                         status: 'PENDING'
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'In progress',
                         status: 'PENDING'
            githubNotify context: "${env.BRANCH_NAME} Result analysis",
                         description: 'In progress',
                         status: 'PENDING'
        }

        stage('pre-test-checks') {
            try {
                environ.set_tls_pr_environment(is_production)
                common.get_all_sh_components(['ubuntu-16.04', 'ubuntu-18.04'])
                common.check_every_all_sh_component_will_be_run()
                common.check_for_bad_words()
                if (env.BRANCH_NAME) {
                    githubNotify context: "${env.BRANCH_NAME} Pre Test Checks",
                                 description: 'OK',
                                 status: 'SUCCESS'
                }
            } catch (err) {
                if (env.BRANCH_NAME) {
                    def description = 'Pre Test Checks failed.'
                    if (err.getMessage().contains('Pre Test Checks')) {
                        description = err.getMessage()
                    }
                    githubNotify context: "${env.BRANCH_NAME} Pre Test Checks",
                                 description: description,
                                 status: 'FAILURE'
                }
                throw (err)
            }
        }

        try {
            stage('tls-testing') {
                run_tls_tests()
            }
        } finally {
            analysis.analyze_results_and_notify_github()
        }
    }
}

/* main job */
def run_job() {
    run_pr_job()
}
