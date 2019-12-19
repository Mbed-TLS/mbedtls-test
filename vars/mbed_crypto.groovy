def run_crypto_tests() {
    node {
        try {
            deleteDir()
            def jobs = [:]

            /* Linux jobs */
            if (env.RUN_LINUX_SCRIPTS == "true") {
                jobs = jobs + gen_jobs.gen_docker_jobs_foreach(
                    'std-make',
                    common.linux_platforms,
                    common.all_compilers,
                    scripts.std_make_test_sh
                )
                jobs = jobs + gen_jobs.gen_docker_jobs_foreach(
                    'std-make-full-config',
                    common.linux_platforms,
                    common.all_compilers,
                    scripts.std_make_full_config_test_sh
                )
                jobs = jobs + gen_jobs.gen_docker_jobs_foreach(
                    'cmake',
                    common.linux_platforms,
                    common.all_compilers,
                    scripts.cmake_test_sh
                )
                jobs = jobs + gen_jobs.gen_docker_jobs_foreach(
                    'cmake-full',
                    common.linux_platforms,
                    common.gcc_compilers,
                    scripts.cmake_full_test_sh
                )
                jobs = jobs + gen_jobs.gen_docker_jobs_foreach(
                    'cmake-asan',
                    common.linux_platforms,
                    common.asan_compilers,
                    scripts.cmake_asan_test_sh
                )
            }

            /* BSD jobs */
            if (env.RUN_FREEBSD == "true") {
                jobs = jobs + gen_jobs.gen_node_jobs_foreach(
                    'gmake',
                    common.bsd_platforms,
                    common.bsd_compilers,
                    scripts.gmake_test_sh
                )
                jobs = jobs + gen_jobs.gen_node_jobs_foreach(
                    'cmake',
                    common.bsd_platforms,
                    common.bsd_compilers,
                    scripts.cmake_test_sh
                )
            }

            /* Windows jobs */
            if (env.RUN_WINDOWS_TEST == "true") {
                jobs = jobs + gen_jobs.gen_windows_jobs_for_pr()
            }

            /* All.sh jobs */
            if (env.RUN_ALL_SH == "true") {
                for (component in common.all_sh_components['ubuntu-16.04']) {
                    jobs = jobs + gen_jobs.gen_all_sh_jobs(
                        'ubuntu-16.04', component
                    )
                }
                jobs = jobs + gen_jobs.gen_all_sh_jobs(
                    'ubuntu-18.04', 'build_mingw'
                )
            }

            if (env.RUN_ABI_CHECK == "true") {
                jobs = jobs + gen_jobs.gen_abi_api_checking_job('ubuntu-16.04')
            }

            /* Deciding whether to run example jobs is handled within this */
            jobs = jobs + gen_jobs.gen_all_example_jobs()

            jobs.failFast = false
            parallel jobs
            if (env.BRANCH_NAME) {
                githubNotify context: "${env.BRANCH_NAME} Crypto Testing",
                             description: 'All tests passed',
                             status: 'SUCCESS'
            }
        } catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            if (env.BRANCH_NAME) {
                githubNotify context: "${env.BRANCH_NAME} Crypto Testing",
                             description: 'Test failure',
                             status: 'FAILURE'
            }
        }
    }
}

/* main job */
def run_pr_job(is_production=true) {
    timestamps {
        if (env.BRANCH_NAME) {
            githubNotify context: "${env.BRANCH_NAME} Pre Test Checks",
                         description: 'Checking if all PR tests can be run',
                         status: 'PENDING'
            githubNotify context: "${env.BRANCH_NAME} Crypto Testing",
                         description: 'In progress',
                         status: 'PENDING'
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'In progress',
                         status: 'PENDING'
        }
        stage('pre-test-checks') {
            node {
                try {
                    environ.set_crypto_pr_environment(is_production)
                    common.get_all_sh_components(['ubuntu-16.04'])
                    if (env.BRANCH_NAME) {
                        githubNotify context: "${env.BRANCH_NAME} Pre Test Checks",
                                     description: 'OK',
                                     status: 'SUCCESS'
                    }
                } catch (err) {
                    if (env.BRANCH_NAME) {
                        githubNotify context: "${env.BRANCH_NAME} Pre Test Checks",
                                     description: 'Base branch out of date. Please rebase',
                                     status: 'FAILURE'
                    }
                    throw (err)
                }
            }
        }

        try {
            if (env.RUN_CRYPTO_TESTS_OF_CRYPTO_PR == "true") {
                stage('crypto-testing') {
                    run_crypto_tests()
                }
            }
            if (env.RUN_TLS_TESTS_OF_CRYPTO_PR == "true") {
                stage('tls-testing') {
                    mbedtls.run_tls_tests_with_crypto_pr(is_production)
                }
            }
        } finally {
            analysis.analyze_results_and_notify_github()
        }
    }
}
