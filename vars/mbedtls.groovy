import groovy.transform.Field

/*
 * This controls the timeout each job has. It does not count the time spent in
 * waiting queues and setting up the environment.
 */
@Field perJobTimeout = [time: 45, unit: 'MINUTES']

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
chmod -R 777 .
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

def gen_windows_jobs(label, script) {
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
set ./tests/scripts/all.sh -m --release-test --keep-going $component
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

/* This runs the job using the main TLS development branch and a Mbed Crypto PR */
def run_job_with_crypto_pr() {
    env.REPO_TO_CHECKOUT = 'tls'
    run_tls_tests()
}

def run_tls_tests() {
    node {
        try {
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
            jobs = jobs + gen_windows_jobs(
                'win32-mingw', scripts.win32_mingw_test_bat
            )
            jobs = jobs + gen_windows_jobs(
                'win32_msvc12_32', scripts.win32_msvc12_32_test_bat
            )
            jobs = jobs + gen_windows_jobs(
                'win32-msvc12_64', scripts.win32_msvc12_64_test_bat
            )
            jobs = jobs + gen_windows_jobs(
                'iar8-mingw', scripts.iar8_mingw_test_bat
            )

            /* All.sh jobs */
            try {
                all_sh_components = common.get_all_sh_components()
                for (component in all_sh_components) {
                    jobs = jobs + gen_all_sh_jobs('ubuntu-16.04', component)
                }
                jobs = jobs + gen_all_sh_jobs('ubuntu-18.04', 'build_mingw')
            } catch (err) {
                echo "Caught: ${err}, not running all.sh tests"
            }

            jobs.failFast = false
            parallel jobs
        } catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
        }
    }
}

/* main job */
def run_job() {
    env.PR_TYPE = 'tls'
    env.REPO_TO_CHECKOUT = 'tls'
    run_tls_tests()
}
