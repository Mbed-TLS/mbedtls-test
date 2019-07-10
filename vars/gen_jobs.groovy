def gen_simple_windows_jobs(label, script) {
    def jobs = [:]

    jobs[label] = {
        node("windows-tls") {
            deleteDir()
            checkout_repo.checkout_repo()
            timeout(time: common.perJobTimeout.time,
                    unit: common.perJobTimeout.unit) {
                bat script
            }
        }
    }
    return jobs
}

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
                            checkout_repo.checkout_repo()
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
                        timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
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
                        checkout_repo.checkout_repo()
                        if (label == 'coverity') {
                            checkout_repo.checkout_coverity_repo()
                        }
                        shell_script = """
set -e
ulimit -f 20971520
export PYTHON=/usr/local/bin/python2.7
""" + shell_script
                        timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
                            sh shell_script
                        }
                    }
                }
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
                    checkout_repo.checkout_repo()
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
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
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

def gen_windows_tests_jobs(build) {
    def jobs = [:]

    jobs["Windows-${build}"] = {
        node("windows-tls") {
            dir("src") {
                deleteDir()
                checkout_repo.checkout_repo()
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
            timeout(time: common.perJobTimeout.time,
                    unit: common.perJobTimeout.unit) {
                bat "python windows_testing.py src logs $env.BRANCH_NAME -b $build"
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
                    checkout_repo.checkout_repo()
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
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
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
