import groovy.transform.Field

/*
 * This controls the timeout each job has. It does not count the time spent in
 * waiting queues and setting up the environment.
 */
@Field perJobTimeout = [time: 45, unit: 'MINUTES']

@Field all_sh_components = []

def gen_docker_jobs_foreach(label, platforms, compilers, script) {
    def jobs = [:]

    for (platform in platforms) {
        for (compiler in compilers) {
            def job_name = "${label}-${compiler}-${platform}"
            def shell_script = sprintf(script, common.compiler_paths[compiler])
            jobs[job_name] = {
                node("mbedtls && ubuntu-16.10-x64") {
                    timestamps {
                        sh 'rm -rf *'
                        deleteDir()
                        common.get_docker_image(platform)
                        dir('src') {
                            checkout_repo.checkout_pr()
                            writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -x
set -v
set -e
ulimit -f 20971520
${shell_script}
exit
"""
                        }
                        timeout(time: perJobTimeout.time,
                                unit: perJobTimeout.unit) {
                            sh """\
chmod +x src/steps.sh
docker run --rm -u \$(id -u):\$(id -g) --entrypoint /var/lib/build/steps.sh \
-w /var/lib/build -v `pwd`/src:/var/lib/build \
-v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh $common.docker_repo:$platform
"""
                        }
                    }
                }
            }
        }
    }
    return jobs
}

def gen_node_jobs_foreach(label, platforms, compilers, script) {
    def jobs = [:]

    for (platform in platforms) {
        for (compiler in compilers) {
            def job_name = "${label}-${compiler}-${platform}"
            def shell_script = sprintf(script, common.compiler_paths[compiler])
            jobs[job_name] = {
                node(platform) {
                    timestamps {
                        deleteDir()
                        checkout_repo.checkout_pr()
                        if (label == 'coverity') {
                            checkout_repo.checkout_coverity_repo()
                        }
                        shell_script = """
set -e
ulimit -f 20971520
export PYTHON=/usr/local/bin/python2.7
""" + shell_script
                        timeout(time: perJobTimeout.time,
                                unit: perJobTimeout.unit) {
                            sh shell_script
                        }
                    }
                }
            }
        }
    }
    return jobs
}

def gen_simple_windows_jobs(label, script) {
    def jobs = [:]

    jobs[label] = {
        node("windows-tls") {
            deleteDir()
            checkout_repo.checkout_pr()
            timeout(time: perJobTimeout.time, unit: perJobTimeout.unit) {
                bat script
            }
        }
    }
    return jobs
}

def gen_windows_tests_jobs(build) {
    def jobs = [:]

    jobs["Windows-${build}"] = {
        node("windows-tls") {
            dir("mbed-crypto") {
                deleteDir()
                checkout_repo.checkout_pr()
            }
            /* The empty files are created to re-create the directory after it
             * and its contents have been removed by deleteDir. */
            dir("logs") {
                deleteDir()
                writeFile file:'_do_not_delete_this_directory.txt', text:''
            }

            dir("worktrees") {
                deleteDir()
                writeFile file:'_do_not_delete_this_directory.txt', text:''
            }
            /* libraryResource loads the file as a string. This is then
             * written to a file so that it can be run on a node. */
            def windows_testing = libraryResource 'windows/windows_testing.py'
            writeFile file: 'windows_testing.py', text: windows_testing
            timeout(time: perJobTimeout.time, unit: perJobTimeout.unit) {
                bat "python windows_testing.py mbed-crypto logs $env.BRANCH_NAME -b $build"
            }
        }
    }
    return jobs
}

def gen_all_sh_jobs(platform, component) {
    def jobs = [:]

    jobs["all_sh-${platform}-${component}"] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            timestamps {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_pr()
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520
git config --global user.email "you@example.com"
git config --global user.name "Your Name"
git init
git add .
git commit -m 'CI code copy'
export LOG_FAILURE_ON_STDOUT=1
set ./tests/scripts/all.sh --seed 4 --keep-going $component
"\$@"
"""
                }
                timeout(time: perJobTimeout.time, unit: perJobTimeout.unit) {
                    sh """\
chmod +x src/steps.sh
docker run -u \$(id -u):\$(id -g) --rm --entrypoint /var/lib/build/steps.sh \
-w /var/lib/build -v `pwd`/src:/var/lib/build \
-v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh \
--cap-add SYS_PTRACE $common.docker_repo:$platform
"""
                }
            }
        }
    }
    return jobs
}

def gen_abi_api_checking_job(platform) {
    def jobs = [:]

    jobs["ABI/API checking"] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            timestamps {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_pr()
                    sh(
                        returnStdout: true,
                        script: "git fetch origin ${CHANGE_TARGET}"
                    ).trim()
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -x
set -v
set -e
ulimit -f 20971520
tests/scripts/list-identifiers.sh --internal
scripts/abi_check.py -o FETCH_HEAD -n HEAD -s identifiers --brief
exit
"""
                }
                timeout(time: perJobTimeout.time, unit: perJobTimeout.unit) {
                    sh """\
chmod +x src/steps.sh
docker run --rm -u \$(id -u):\$(id -g) --entrypoint /var/lib/build/steps.sh \
-w /var/lib/build -v `pwd`/src:/var/lib/build \
-v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh $common.docker_repo:$platform
"""
                }
            }
        }
    }
    return jobs
}

def run_crypto_tests() {
    node {
        try {
            githubNotify context: 'Crypto Testing',
                         description: 'In progress',
                         status: 'PENDING'
            deleteDir()

            /* Linux jobs */
            def jobs = gen_docker_jobs_foreach(
                'std-make',
                common.linux_platforms,
                common.all_compilers,
                scripts.std_make_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'std-make-full-config',
                common.linux_platforms,
                common.all_compilers,
                scripts.std_make_full_config_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'cmake',
                common.linux_platforms,
                common.all_compilers,
                scripts.cmake_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'cmake-full',
                common.linux_platforms,
                common.gcc_compilers,
                scripts.cmake_full_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'cmake-asan',
                common.linux_platforms,
                common.asan_compilers,
                scripts.cmake_asan_test_sh
            )

            /* BSD jobs */
            jobs = jobs + gen_node_jobs_foreach(
                'gmake',
                common.bsd_platforms,
                common.bsd_compilers,
                scripts.gmake_test_sh
            )
            jobs = jobs + gen_node_jobs_foreach(
                'cmake',
                common.bsd_platforms,
                common.bsd_compilers,
                scripts.cmake_test_sh
            )

            /* Windows jobs */
            jobs = jobs + gen_simple_windows_jobs(
                'win32-mingw', scripts.win32_mingw_test_bat
            )
            jobs = jobs + gen_simple_windows_jobs(
                'win32_msvc12_32', scripts.win32_msvc12_32_test_bat
            )
            jobs = jobs + gen_simple_windows_jobs(
                'win32-msvc12_64', scripts.win32_msvc12_64_test_bat
            )
            jobs = jobs + gen_simple_windows_jobs(
                'iar8-mingw', scripts.iar8_mingw_test_bat
            )
            for (build in ['mingw', '2013']) {
                jobs = jobs + gen_windows_tests_jobs(build)
            }

            /* Coverity jobs */
            jobs = jobs + gen_node_jobs_foreach(
                'coverity',
                common.coverity_platforms,
                common.coverity_compilers,
                scripts.std_coverity_sh
            )

            /* All.sh jobs */
            for (component in all_sh_components) {
                jobs = jobs + gen_all_sh_jobs('ubuntu-16.04', component)
            }
            jobs = jobs + gen_all_sh_jobs('ubuntu-18.04', 'build_mingw')

            jobs = jobs + gen_abi_api_checking_job('ubuntu-16.04')

            jobs.failFast = false
            parallel jobs
            githubNotify context: 'Crypto Testing',
                         description: 'All tests passed',
                         status: 'SUCCESS'
        } catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
            githubNotify context: 'Crypto Testing',
                         description: 'Test failure',
                         status: 'FAILURE'
        }
    }
}

/* main job */
def run_job() {
    githubNotify context: 'Pre Test Checks',
                 description: 'Checking if all PR tests can be run',
                 status: 'PENDING'
    githubNotify context: 'Crypto Testing',
                 description: 'Not started',
                 status: 'PENDING'
    githubNotify context: 'TLS Testing',
                 description: 'Not started',
                 status: 'PENDING'
    stage('pre-test-checks') {
        node {
            try {
                env.PR_TYPE = 'crypto'
                env.REPO_TO_CHECKOUT = 'crypto'
                /* Get components of all.sh */
                dir('mbedtls') {
                    deleteDir()
                    checkout_repo.checkout_pr()
                    all_sh_help = sh(
                        script: "./tests/scripts/all.sh --help",
                        returnStdout: true
                    )
                    if (all_sh_help.contains('list-components')) {
                        all_sh_components = sh(
                            script: "./tests/scripts/all.sh --list-components",
                            returnStdout: true
                        ).trim().split('\n')
                    } else {
                        def message = 'Base branch out of date. Please rebase'
                        githubNotify context: 'Pre Test Checks',
                                     description: message,
                                     status: 'FAILURE'
                        githubNotify context: 'Crypto Testing',
                                     description: 'Not run',
                                     status: 'FAILURE'
                        githubNotify context: 'TLS Testing',
                                     description: 'Not run',
                                     status: 'FAILURE'
                        error(message)
                    }
                }

                githubNotify context: 'Pre Test Checks',
                             description: 'OK',
                             status: 'SUCCESS'
            } catch (err) {
                throw (err)
            }
        }
    }
    stage('crypto-testing') {
        run_crypto_tests()
    }
    stage('tls-testing') {
        try {
            githubNotify context: 'TLS Testing',
                         description: 'In progress',
                         status: 'PENDING'
            mbedtls.run_job_with_crypto_pr()
            githubNotify context: 'TLS Testing',
                         description: 'All tests passed',
                         status: 'SUCCESS'
        } catch (err) {
            githubNotify context: 'TLS Testing',
                         description: 'Test failure',
                         status: 'FAILURE'
        }
    }
}
