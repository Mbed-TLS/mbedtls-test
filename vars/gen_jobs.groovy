/*
 *  Copyright (c) 2019-2022, Arm Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
 */

import groovy.transform.Field

// Keep track of builds that fail
@Field failed_builds = [:]

//Record coverage details for reporting
@Field coverage_details = ['coverage': 'Code coverage job did not run']

def gen_simple_windows_jobs(label, script) {
    def jobs = [:]

    jobs[label] = {
        node("windows") {
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
                node('container-host') {
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

def node_label_for_platform(platform) {
    switch (platform) {
    case ~/^(debian|ubuntu)(-.*)?/: return 'container-host';
    case ~/^freebsd(-.*)?/: return 'freebsd';
    case ~/^windows(-.*)?/: return 'windows';
    default: return platform;
    }
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
    def shorthands = [
        "ubuntu-16.04": "u16",
        "ubuntu-18.04": "u18",
        "ubuntu-20.04": "u20",
        "freebsd": "fbsd",
    ]
    /* Default to the full platform hame is a shorthand is not found */
    def shortplat = shorthands.getOrDefault(platform, platform)
    def job_name = "${label_prefix}all_${shortplat}-${component}"
    def use_docker = platform_has_docker(platform)
    def extra_setup_code = ''
    def node_label = node_label_for_platform(platform)

    if (platform_lacks_tls_tools(platform)) {
        /* The check_tools function in all.sh insists on the existence of the
         * TLS tools, even if no test happens to use them. Passing 'false'
         * pacifies check_tools, but will cause tests to fail if they
         * do try to use it. */
        extra_setup_code += '''
export OPENSSL=false GNUTLS_CLI=false GNUTLS_SERV=false
'''
    }

    if (platform.contains('bsd')) {
        /* At the time of writing, all.sh assumes that make is GNU make.
         * But on FreeBSD, make is BSD make and gmake is GNU make.
         * So put a "make" which is GNU make ahead of the system "make"
         * in $PATH. */
        extra_setup_code += '''
[ -d bin ] || mkdir bin
[ -x bin/make ] || ln -s /usr/local/bin/gmake bin/make
PATH="$PWD/bin:$PATH"
echo >&2 'Note: "make" will run /usr/local/bin/gmake (GNU make)'
'''
        /* At the time of writing, `all.sh test_clang_opt` fails on FreeBSD
         * because it uses `-std=c99 -pedantic` and Clang on FreeBSD
         * thinks that our code is trying to use a C11 feature
         * (static_assert). Which is true, but harmless since our code
         * checks for this feature's availability. As a workaround,
         * instrument the compilation not to treat the use of C11 features
         * as errors, only as warnings.
         * https://github.com/ARMmbed/mbedtls/issues/3693
         */
        extra_setup_code += '''
# We added the bin/ subdirectory to the beginning of $PATH above.
cat >bin/clang <<'EOF'
#!/bin/sh
exec /usr/bin/clang -Wno-error=c11-extensions "$@"
EOF
chmod +x bin/clang
echo >&2 'Note: "clang" will run /usr/bin/clang -Wno-error=c11-extensions'
'''
    }

    if (common.has_min_requirements) {
        extra_setup_code += '''
scripts/min_requirements.py --user
'''
    }

    jobs[job_name] = {
        node(node_label) {
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
export MBEDTLS_TEST_OUTCOME_FILE='${job_name}-outcome.csv'
${extra_setup_code}
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
        node("windows") {
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

                if (common.has_min_requirements) {
                    dir("src") {
                        timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
                            bat "python scripts\\min_requirements.py"
                        }
                    }
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
        node('container-host') {
            try {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_repo()
                    /* The credentials here are the SSH credentials for accessing the repositories.
                       They are defined at {JENKINS_URL}/credentials */
                    withCredentials([sshUserPrivateKey(credentialsId: "742b7080-e1cc-41c6-bf55-efb72013bc28", keyFileVariable: 'keyfile')]) {
                        sh "GIT_SSH_COMMAND=\"ssh -i ${keyfile}\" git fetch origin ${CHANGE_TARGET}"
                    }
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520

if [ -e scripts/min_requirements.py ]; then
    scripts/min_requirements.py --user
fi

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

    jobs[job_name] = {
        node('container-host') {
            try {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_repo()
                    writeFile file: 'steps.sh', text: '''#!/bin/sh
set -eux
ulimit -f 20971520

if [ -e scripts/min_requirements.py ]; then
    scripts/min_requirements.py --user
fi

if grep -q -F coverage-summary.txt tests/scripts/basic-build-test.sh; then
    # New basic-build-test, generates coverage-summary.txt
    ./tests/scripts/basic-build-test.sh
else
    # Old basic-build-test, only prints the coverage summary to stdout
    { stdbuf -oL ./tests/scripts/basic-build-test.sh 2>&1; echo $?; } |
      tee basic-build-test.log
    [ "$(tail -n1 basic-build-test.log)" -eq 0 ]
    sed -n '/^Test Report Summary/,$p' basic-build-test.log >coverage-summary.txt
    rm basic-build-test.log
fi
'''
                    sh 'chmod +x steps.sh'
                }
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
                    try {
                        sh common.docker_script(
                                platform, "/var/lib/build/steps.sh"
                        )
                        dir('src') {
                            String coverage_log = readFile('coverage-summary.txt')
                            coverage_details['coverage'] = coverage_log.substring(
                                coverage_log.indexOf('\nCoverage\n') + 1
                            )
                        }
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

def gen_coverity_push_jobs() {
    def jobs = [:]
    def job_name = "coverity-push"

    if (env.MBED_TLS_BRANCH == "development") {
        jobs[] = {
            node('container-host') {
                try {
                    dir("src") {
                        deleteDir()
                        checkout_repo.checkout_repo()
                        sshagent([env.GIT_CREDENTIALS_ID]) {
                            sh 'git push origin HEAD:coverity_scan'
                        }
                    }
                } catch (err) {
                    failed_builds[job_name]= true
                    throw (err)
                } finally {
                    deleteDir()
                }
            }
        }
    }

    return jobs
}

def gen_release_jobs(label_prefix='', run_examples=true) {
    def jobs = [:]

    common.get_branch_information()

    if (env.RUN_BASIC_BUILD_TEST == "true") {
        jobs = jobs + gen_code_coverage_job('ubuntu-16.04');
    }

    if (env.RUN_ALL_SH == "true") {
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
            for (component in common.freebsd_all_sh_components) {
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

    if (env.PUSH_COVERITY == "true") {
        jobs = jobs + gen_coverity_push_jobs()
    }

    return jobs
}

def gen_dockerfile_builder_job(platform, overwrite=false) {
    def jobs = [:]
    def dockerfile = libraryResource "docker_files/$platform/Dockerfile"

    def tag = "$platform-${common.git_hash_object(dockerfile)}"
    common.docker_tags[platform] = tag

    jobs[platform] = {
        /* Take the lock on the master node, so we don't tie up an executor while waiting */
        lock(tag) {
            node('dockerfile-builder') {
                def image_exists = false
                if (!overwrite) {
                    def test_image_exists_sh = "aws ecr describe-images --repository-name $common.docker_repo_name --image-ids imageTag=$tag"
                    image_exists = sh(script: test_image_exists_sh, returnStatus: true) == 0
                }
                if (overwrite || !image_exists) {
                    dir('docker') {
                        deleteDir()
                        writeFile file: 'Dockerfile', text: dockerfile
                        sh """\
docker build -t $common.docker_repo:$tag .
aws ecr get-login-password | docker login --username AWS --password-stdin $common.docker_ecr
docker push $common.docker_repo:$tag
"""
                    }
                }
            }
        }
    }
    return jobs
}
