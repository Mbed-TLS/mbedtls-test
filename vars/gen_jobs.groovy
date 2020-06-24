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
            } finally {
                deleteDir()
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
                    try {
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
                            sh 'chmod +x steps.sh'
                        }
                        timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
                            try {
                                sh common.docker_script(
                                    platform, "/var/lib/build/steps.sh"
                                )
                            } finally {
                                dir('src/tests/') {
                                    common.archive_zipped_log_files(job_name)
                                }
                            }
                        }
                    } catch (err) {
                        failed_builds[job_name] = true
                        throw (err)
                    } finally {
                        deleteDir()
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
                    try {
                        deleteDir()
                        checkout_repo.checkout_repo()
                        shell_script = """\
ulimit -f 20971520
export PYTHON=/usr/local/bin/python2.7
""" + shell_script
                        timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
                            sh shell_script
                        }
                    } catch (err) {
                        failed_builds[job_name] = true
                        throw (err)
                    } finally {
                        deleteDir()
                    }
                }
            }
        }
    }
    return jobs
}

def platform_has_docker(platform) {
    def os = platform.replaceFirst(/-.*/, "")
    return ['debian', 'ubuntu'].contains(os)
}

def platform_lacks_tls_tools(platform) {
    def os = platform.replaceFirst(/-.*/, "")
    return ['freebsd'].contains(os)
}

def gen_all_sh_jobs(platform, component, label_prefix='') {
    def jobs = [:]
    def job_name = "${label_prefix}all_sh-${platform}-${component}"
    def use_docker = platform_has_docker(platform)
    def extra_env = ''

    if (platform_lacks_tls_tools(platform)) {
        /* The check_tools function in all.sh insists on the existence of the
         * TLS tools, even if no test happens to use them. Passing 'false'
         * pacifies check_tools, but will cause tests to fail if they
         * do try to use it. */
        extra_env += ' OPENSSL=false GNUTLS_CLI=false GNUTLS_SERV=false'
    }

    jobs[job_name] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            try {
                deleteDir()
                if (use_docker) {
                    common.get_docker_image(platform)
                }
                dir('src') {
                    checkout_repo.checkout_repo()
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520
export MBEDTLS_TEST_OUTCOME_FILE='${job_name}-outcome.csv' ${extra_env}
./tests/scripts/all.sh --seed 4 --keep-going $component
"""
                    sh 'chmod +x steps.sh'
                }
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
                    try {
                        if (use_docker) {
                            sh common.docker_script(
                                platform, "/var/lib/build/steps.sh"
                            )
                        } else {
                            dir('src') {
                                sh './steps.sh'
                            }
                        }
                    } finally {
                        dir('src') {
                            analysis.stash_outcomes(job_name)
                        }
                        dir('src/tests/') {
                            common.archive_zipped_log_files(job_name)
                        }
                    }
                }
            } catch (err) {
                failed_builds[job_name] = true
                throw (err)
            } finally {
                deleteDir()
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
            } finally {
                deleteDir()
            }
        }
    }
    return jobs
}

def gen_windows_jobs(label_prefix='') {
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
    for (build in common.get_supported_windows_builds()) {
        jobs = jobs + gen_windows_testing_job(build, label_prefix)
    }
    return jobs
}

def gen_abi_api_checking_job(platform) {
    def jobs = [:]
    def job_name = "ABI-API-checking"

    jobs[job_name] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            try {
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
                    sh 'chmod +x steps.sh'
                }
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
                    sh common.docker_script(
                        platform, "/var/lib/build/steps.sh"
                    )
                }
            } catch (err) {
                failed_builds[job_name] = true
                throw (err)
            } finally {
                deleteDir()
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
                    sh 'chmod +x steps.sh'
                }
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
                    try {
                        coverage_log = sh(
                            script: common.docker_script(
                                platform, "/var/lib/build/steps.sh"
                            ),
                            returnStdout: true
                        )
                        coverage_details['coverage'] = coverage_log.substring(
                            coverage_log.indexOf('Test Report Summary')
                        )
                        coverage_details['coverage'] = coverage_details['coverage'].substring(
                            coverage_details['coverage'].indexOf('Coverage')
                        )
                    } finally {
                        echo coverage_log
                        dir('src/tests/') {
                            common.archive_zipped_log_files(job_name)
                        }
                    }
                }
            } catch (err) {
                failed_builds[job_name] = true
                throw (err)
            } finally {
                deleteDir()
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
    def job_name = "mbed-os-${example}-${platform}-${compiler}"

    jobs[job_name] = {
        node(compiler) {
            try {
                deleteDir()
/* Create python virtual environment and install mbed tools */
                sh """\
ulimit -f 20971520
virtualenv $WORKSPACE/mbed-venv
. $WORKSPACE/mbed-venv/bin/activate
pip install mbed-cli
pip install mbed-host-tests
"""
                dir('mbed-os-example') {
                    deleteDir()
                    checkout_repo.checkout_mbed_os_example_repo(repo, branch)
                    dir(example) {
/* If the job is targeting an example repo, then we wish to use the versions
 * of Mbed OS, TLS and Crypto specified by the mbed-os.lib file. */
                        if (env.TARGET_REPO == 'example') {
                            sh """\
ulimit -f 20971520
. $WORKSPACE/mbed-venv/bin/activate
mbed config root .
mbed deploy -vv
"""
                        } else {
/* If the job isn't targeting an example repo, the versions of Mbed OS, TLS and
 * Crypto will be specified by the job. We remove mbed-os.lib so we aren't
 * checking it out twice. Mbed deploy is still run in case other libraries
 * are required to be deployed. We then check out Mbed OS, TLS and Crypto
 * according to the job parameters. */
                            sh """\
ulimit -f 20971520
. $WORKSPACE/mbed-venv/bin/activate
rm -f mbed-os.lib
mbed config root .
mbed deploy -vv
"""
                            dir('mbed-os') {
                                deleteDir()
                                checkout_repo.checkout_mbed_os()
/* Check that python requirements are up to date */
                                sh """\
ulimit -f 20971520
. $WORKSPACE/mbed-venv/bin/activate
pip install -r requirements.txt
"""
                            }
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
. $WORKSPACE/mbed-venv/bin/activate
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
                failed_builds[job_name] = true
                throw (err)
            } finally {
                deleteDir()
            }
        }
    }
    return jobs
}

def gen_release_jobs(label_prefix='', run_examples=true) {
    def jobs = [:]

    if (env.RUN_BASIC_BUILD_TEST == "true") {
        jobs = jobs + gen_code_coverage_job('ubuntu-16.04');
    }

    if (env.RUN_ALL_SH == "true") {
        common.get_all_sh_components(['ubuntu-16.04', 'ubuntu-18.04'])
        for (component in common.available_all_sh_components['ubuntu-16.04']) {
            jobs = jobs + gen_all_sh_jobs('ubuntu-16.04', component, label_prefix)
        }
        for (component in (common.available_all_sh_components['ubuntu-18.04'] -
                           common.available_all_sh_components['ubuntu-16.04'])) {
            jobs = jobs + gen_all_sh_jobs('ubuntu-18.04', component, label_prefix)
        }
    }

    /* FreeBSD all.sh jobs */
    if (env.RUN_FREEBSD == "true") {
        for (platform in common.bsd_platforms) {
            for (component in common.other_platform_all_sh_components) {
                jobs = jobs + gen_all_sh_jobs(platform, component, label_prefix)
            }
        }
    }

    if (env.RUN_WINDOWS_TEST == "true") {
        jobs = jobs + gen_windows_jobs(label_prefix)
    }

    if (run_examples) {
        jobs = jobs + gen_all_example_jobs()
    }

    return jobs
}
