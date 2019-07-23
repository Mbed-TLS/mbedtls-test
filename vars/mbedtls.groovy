import groovy.transform.Field

@Field all_sh_components = []

/* This runs the job using the main TLS development branch and a Mbed Crypto PR */
def run_tls_tests_with_crypto_pr() {
    env.REPO_TO_CHECKOUT = 'tls'
    all_sh_components = common.get_all_sh_components()
    run_tls_tests()
}

def run_tls_tests() {
    node {
        try {
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'In progress',
                         status: 'PENDING'
            deleteDir()

            /* Linux jobs */
            def jobs = gen_jobs.gen_docker_jobs_foreach(
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

            /* BSD jobs */
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

            /* Windows jobs */
            jobs = jobs + gen_jobs.gen_simple_windows_jobs(
                'win32-mingw', scripts.win32_mingw_test_bat
            )
            jobs = jobs + gen_jobs.gen_simple_windows_jobs(
                'win32_msvc12_32', scripts.win32_msvc12_32_test_bat
            )
            jobs = jobs + gen_jobs.gen_simple_windows_jobs(
                'win32-msvc12_64', scripts.win32_msvc12_64_test_bat
            )
            jobs = jobs + gen_jobs.gen_simple_windows_jobs(
                'iar8-mingw', scripts.iar8_mingw_test_bat
            )

            /* All.sh jobs */
            for (component in all_sh_components) {
                jobs = jobs + gen_jobs.gen_all_sh_jobs(
                    'ubuntu-16.04', component
                )
            }
            jobs = jobs + gen_jobs.gen_all_sh_jobs(
                'ubuntu-18.04', 'build_mingw'
            )

            jobs.failFast = false
            parallel jobs
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'All tests passed',
                         status: 'SUCCESS'
        } catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            githubNotify context: "${env.BRANCH_NAME} TLS Testing",
                         description: 'Test failure',
                         status: 'FAILURE'
        }
    }
}

/* main job */
def run_pr_job() {
    githubNotify context: 'Pre Test Checks',
                 description: 'Checking if all PR tests can be run',
                 status: 'PENDING'
    stage('pre-test-checks') {
        node {
            try {
                environ.set_tls_pr_environment()
                all_sh_components = common.get_all_sh_components()
                githubNotify context: 'Pre Test Checks',
                             description: 'OK',
                             status: 'SUCCESS'
            } catch (err) {
                githubNotify context: 'Pre Test Checks',
                             description: 'Base branch out of date. Please rebase',
                             status: 'FAILURE'
                throw (err)
            }
        }
    }
    stage('tls-testing') {
        run_tls_tests()
    }
}

/* main job */
def run_job() {
    run_pr_job()
}
