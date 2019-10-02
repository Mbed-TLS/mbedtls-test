import groovy.transform.Field

// Keep track of builds that fail
@Field failed_builds = [:]

//Record coverage details for reporting
@Field coverage_details = ['coverage': 'Code coverage job did not run']

def gen_simple_windows_jobs(label, script) {
    def jobs = [:]

    jobs[label] = {
        node("windows-tls") {
            try {
                dir("src") {
                    deleteDir()
                    checkout_repo.checkout_repo()
                    timeout(time: common.perJobTimeout.time,
                            unit: common.perJobTimeout.unit) {
                        bat script
                    }
                }
            } catch (err) {
                failed_builds[label] = true
                throw (err)
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
                        deleteDir()
                        common.get_docker_image(platform)
                        dir('src') {
                            checkout_repo.checkout_repo()
                            writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520
${shell_script}
"""
                        }
                        timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
                            try {
                                sh """\
chmod +x src/steps.sh
docker run --rm -u \$(id -u):\$(id -g) --entrypoint /var/lib/build/steps.sh \
    -w /var/lib/build -v `pwd`/src:/var/lib/build \
    -v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh $common.docker_repo:$platform
"""
                            } finally {
                                dir('src/tests/') {
                                    common.archive_zipped_log_files(job_name)
                                }
                            }
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
                        shell_script = """
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

def gen_all_sh_jobs(platform, component, label_prefix='') {
    def jobs = [:]
    def job_name = "${label_prefix}all_sh-${platform}-${component}"

    jobs[job_name] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            try {
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
export MBEDTLS_TEST_OUTCOME_FILE='${job_name}-outcome.csv'
set ./tests/scripts/all.sh --seed 4 --keep-going $component
"\$@"
"""
                    }
                    timeout(time: common.perJobTimeout.time,
                            unit: common.perJobTimeout.unit) {
                        try {
                            sh """\
chmod +x src/steps.sh
docker run -u \$(id -u):\$(id -g) --rm --entrypoint /var/lib/build/steps.sh \
    -w /var/lib/build -v `pwd`/src:/var/lib/build \
    -v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh \
    --cap-add SYS_PTRACE $common.docker_repo:$platform
"""
                        } finally {
                            dir('src') {
                                analysis.stash_outcomes(job_name)
                            }
                            dir('src/tests/') {
                                common.archive_zipped_log_files(job_name)
                            }
                        }
                    }
                }
            } catch (err) {
                failed_builds[job_name] = true
                throw (err)
            }
        }
    }
    return jobs
}

def gen_windows_testing_job(build, label_prefix='') {
    def jobs = [:]
    def job_name = "${label_prefix}Windows-${build}"

    jobs[job_name] = {
        node("windows-tls") {
            try {
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
                    bat "python windows_testing.py src logs -b $build"
                }
            } catch (err) {
                failed_builds[job_name] = true
                throw (err)
            }
        }
    }
    return jobs
}

def gen_windows_jobs_for_pr(label_prefix='') {
    def jobs = [:]
    jobs = jobs + gen_simple_windows_jobs(
        label_prefix + 'win32-mingw', scripts.win32_mingw_test_bat
    )
    jobs = jobs + gen_simple_windows_jobs(
        label_prefix + 'win32_msvc12_32', scripts.win32_msvc12_32_test_bat
    )
    jobs = jobs + gen_simple_windows_jobs(
        label_prefix + 'win32-msvc12_64', scripts.win32_msvc12_64_test_bat
    )
    jobs = jobs + gen_windows_jobs_for_release(label_prefix)
    return jobs
}

def gen_windows_jobs_for_release(label_prefix='') {
    def jobs = [:]
    for (build in common.get_supported_windows_builds()) {
        jobs = jobs + gen_windows_testing_job(build, label_prefix)
    }
    jobs = jobs + gen_simple_windows_jobs(
        label_prefix + 'iar8-mingw', scripts.iar8_mingw_test_bat
    )
    return jobs
}

def gen_abi_api_checking_job(platform) {
    def jobs = [:]
    def job_name = "ABI-API-checking"

    jobs[job_name] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            timestamps {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_repo()
                    if (env.TARGET_REPO == 'crypto' && env.REPO_TO_CHECKOUT == 'tls') {
                        sh "git fetch origin development"
                    } else {
                        sh "git fetch origin ${CHANGE_TARGET}"
                    }
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520
tests/scripts/list-identifiers.sh --internal
scripts/abi_check.py -o FETCH_HEAD -n HEAD -s identifiers --brief
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

def gen_code_coverage_job(platform) {
    def jobs = [:]
    def job_name = 'code-coverage'
    def coverage_log = ''

    jobs[job_name] = {
        node('mbedtls && ubuntu-16.10-x64') {
            try {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_repo()
                    writeFile file: 'steps.sh', text: '''#!/bin/sh
set -eux
ulimit -f 20971520
./tests/scripts/basic-build-test.sh 2>&1
'''
                }
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
                    coverage_log = sh returnStdout: true, script: """
chmod +x src/steps.sh
docker run -u \$(id -u):\$(id -g) --rm --entrypoint /var/lib/build/steps.sh \
    -w /var/lib/build -v `pwd`/src:/var/lib/build \
    -v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh $common.docker_repo:$platform
"""
                }
                coverage_details['coverage'] = coverage_log.substring(
                    coverage_log.indexOf('Test Report Summary')
                )
                coverage_details['coverage'] = coverage_details['coverage'].substring(
                    coverage_details['coverage'].indexOf('Coverage')
                )
            } catch (err) {
                failed_builds[job_name] = true
                throw (err)
            } finally {
                echo coverage_log
                dir('src/tests/') {
                    common.archive_zipped_log_files(job_name)
                }
            }
        }
    }
    return jobs
}

/* Mbed OS Example job generation */
def gen_all_example_jobs() {
    def jobs = [:]

    examples.examples.each { example ->
        if (example.value['should_run'] == 'true') {
            for (compiler in example.value['compilers']) {
                for (platform in example.value['platforms']()) {
                    if (examples.raas_for_platform[platform]) {
                        jobs = jobs + gen_mbed_os_example_job(
                            example.value['repo'],
                            example.value['branch'],
                            example.key, compiler, platform,
                            examples.raas_for_platform[platform]
                        )
                    }
                }
            }
        }
    }
    return jobs
}

def gen_mbed_os_example_job(repo, branch, example, compiler, platform, raas) {
    def jobs = [:]

    jobs["${example}-${platform}-${compiler}"] = {
        node(compiler) {
            try {
                timestamps {
                    deleteDir()
                    checkout_repo.checkout_parametrized_repo(repo, branch)
                    dir(example) {
/* This script appears to do nothing, however it is needed in a few cases.
 * We wish to deploy specific versions of Mbed OS, TLS and Crypto, so we
 * remove mbed-os.lib to not deploy it twice. Mbed deploy is still needed in
 * case other libraries exist to be deployed. */
                        sh """\
ulimit -f 20971520
rm -f mbed-os.lib
mbed config root .
mbed deploy -vv
"""
                        dir('mbed-os') {
                            deleteDir()
                            checkout_repo.checkout_mbed_os()
                        }
                        timeout(time: common.perJobTimeout.time +
                                      common.perJobTimeout.raasOffset,
                                unit: common.perJobTimeout.unit) {
                            def tag_filter = ""
                            if (example == 'atecc608a') {
                                tag_filter = "--tag-filters HAS_CRYPTOKIT"
                            }
                            sh """\
ulimit -f 20971520
mbed compile -m ${platform} -t ${compiler}
"""
                            for (int attempt = 1; attempt <= 3; attempt++) {
                                try {
                                    sh """\
ulimit -f 20971520
if [ -e BUILD/${platform}/${compiler}/${example}.bin ]
then
    BINARY=BUILD/${platform}/${compiler}/${example}.bin
else
    if [ -e BUILD/${platform}/${compiler}/${example}.hex ]
    then
        BINARY=BUILD/${platform}/${compiler}/${example}.hex
    fi
fi

export RAAS_PYCLIENT_FORCE_REMOTE_ALLOCATION=1
export RAAS_PYCLIENT_ALLOCATION_QUEUE_TIMEOUT=3600
mbedhtrun -m ${platform} ${tag_filter} \
-g raas_client:https://${raas}.mbedcloudtesting.com:443 -P 1000 --sync=0 -v \
    --compare-log ../tests/${example}.log -f \$BINARY
"""
                                    break
                                } catch (err) {
                                    if (attempt == 3) throw (err)
                                }
                            }
                        }
                    }
                }
            } catch (err) {
                failed_builds["${example}-${platform}-${compiler}"] = true
                throw (err)
            }
        }
    }
    return jobs
}

def gen_release_jobs() {
    def jobs = [:]

    if (RUN_BASIC_BUILD_TEST == "true") {
        jobs = jobs + gen_code_coverage_job('ubuntu-16.04');
    }

    if (RUN_ALL == "true") {
        all_sh_components = common.get_all_sh_components()
        for (component in all_sh_components) {
            jobs = jobs + gen_all_sh_jobs('ubuntu-16.04', component)
        }
        jobs = jobs + gen_all_sh_jobs('ubuntu-18.04', 'build_mingw')
    }

    if (RUN_WINDOWS_TEST == "true") {
        jobs = jobs + gen_windows_jobs_for_release()
    }

    jobs = jobs + gen_all_example_jobs()

    return jobs
}
