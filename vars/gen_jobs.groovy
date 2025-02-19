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

import java.util.concurrent.Callable

import net.sf.json.JSONObject
import hudson.AbortException

import org.mbed.tls.jenkins.BranchInfo

static <T> Map<String, Closure<T>> job(String label, Closure<T> body) {
    return Collections.singletonMap(label, body)
}

private Map<String, Callable<Void>> instrumented_node_job(String node_label, String job_name, Callable<Void> body) {
    return job(job_name) {
        analysis.node_record_timestamps(node_label, job_name, body)
    }
}

Map<String, Callable<Void>> gen_simple_windows_jobs(BranchInfo info, String label, String script) {
    return instrumented_node_job('windows', label) {
        try {
            dir('src') {
                deleteDir()
                checkout_repo.checkout_tls_repo(info)
                timeout(time: common.perJobTimeout.time,
                        unit: common.perJobTimeout.unit) {
                    analysis.record_inner_timestamps('windows', label) {
                        bat script
                    }
                }
            }
        } catch (err) {
            info.failed_builds << label
            throw (err)
        } finally {
            deleteDir()
        }
    }
}

def node_label_for_platform(platform) {
    switch (platform) {
    case ~/^(debian|ubuntu|arm-compilers).*-amd64/: return 'container-host';
    case ~/^(debian|ubuntu|arm-compilers).*-arm64/: return 'container-host-arm64';
    case ~/^freebsd(-.*)?/: return 'freebsd';
    case ~/^windows(-.*)?/: return 'windows';
    default: return platform;
    }
}

boolean platform_has_docker(String platform) {
    return platform.startsWithAny('debian', 'ubuntu', 'arm-compilers')
}

def platform_lacks_tls_tools(platform) {
    return platform.startsWithAny('freebsd', 'arm-compilers')
}

// gen_docker_job(info, job_name, platform, script_in_docker, ...)
/**
 * Construct a job that runs a script in Docker.
 *
 * @return
 *     A one-element map mapping job_name to a closure that runs the job.
 *
 * @param info
 *     A BranchInfo object describing the branch to test.
 * @param job_name
 *     The name to use for the job. It is used as a key in
 *     the returned map, as a name in Jenkins reports and logs, to
 *     construct file names and anywhere else this function needs
 *     a presumed unique job name.
 * @param platform
 *     The name of the Docker image.
 * @param script_in_docker
 *     A shell script to run in the Docker image.
 *
 * @param hooks
 *     Named parameters
 *     <dl>
 *         <dt>{@code post_checkout}</dt><dd>
 *             Hook that runs after checking out the code to test.
 *         </dd>
 *         <dt>{@code post_success}</dt><dd>
 *             Hook that runs after running the script in Docker, if
 *             that script succeeds.
 *         </dd>
 *         <dt>{@code post_execution}</dt><dd>
 *             Hook that runs after running the script in Docker,
 *             whether it succeeded or not. It can check the job's status by querying
 *             {@link BranchInfo#failed_builds}, which contains {@code job_name}
 *             if the job failed. This hook should not throw an exception.
 *         </dd>
 *     </dl>
 *
 *     All hook parameters are closures that are called with no arguments.
 *     They can be null, in which case the hook does nothing. The code runs
 *     on a 'container-host' executor, in the directory containing the
 *     source code.
 */
Map<String, Callable<Void>> gen_docker_job(Map<String, Closure> hooks,
                                           BranchInfo info,
                                           String job_name,
                                           String node_label = 'container-host',
                                           String platform,
                                           String script_in_docker) {
    return instrumented_node_job(node_label, job_name) {
        try {
            stage('checkout') {
                deleteDir()
                common.get_docker_image(platform)
                dir('src') {
                    checkout_repo.checkout_repo(info)
                    if (hooks.post_checkout) {
                        hooks.post_checkout()
                    }
                }
            }
            stage(job_name) {
                dir('src') {
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520

if [ -e scripts/min_requirements.py ]; then
python3 -m venv --system-site-packages --without-pip venv
export PATH="\$PWD/venv/bin:\$PATH"
python3 scripts/min_requirements.py ${info.python_requirements_override_file}
fi
""" + script_in_docker
                    sh 'chmod +x steps.sh'
                }
                timeout(time: common.perJobTimeout.time,
                    unit: common.perJobTimeout.unit) {
                    try {
                        analysis.record_inner_timestamps(node_label, job_name) {
                            sh common.docker_script(
                                platform, "/var/lib/build/steps.sh"
                            )
                        }
                        if (hooks.post_success) {
                            dir('src') {
                                hooks.post_success()
                            }
                        }
                    } finally {
                        dir('src/tests/') {
                            common.archive_zipped_log_files(job_name)
                        }
                    }
                }
            }
        } catch (err) {
            info.failed_builds << job_name
            throw (err)
        } finally {
            if (hooks.post_execution) {
                stage('post-execution') {
                    dir('src') {
                        hooks.post_execution()
                    }
                }
            }
            deleteDir()
        }
    }
}

def gen_all_sh_jobs(BranchInfo info, platform, component, label_prefix='') {
    def shorthands = [
        "arm-compilers-amd64": "armcc",
        "ubuntu-16.04-amd64": "u16",
        "ubuntu-16.04-arm64": "u16-arm",
        "ubuntu-18.04-amd64": "u18",
        "ubuntu-18.04-arm64": "u18-arm",
        "ubuntu-20.04-amd64": "u20",
        "ubuntu-20.04-arm64": "u20-arm",
        "ubuntu-22.04-amd64": "u22",
        "ubuntu-22.04-arm64": "u22-arm",
        "freebsd": "fbsd",
    ]
    /* Default to the full platform hame is a shorthand is not found */
    def shortplat = shorthands.getOrDefault(platform, platform)
    def job_name = "${label_prefix}all_${shortplat}-${component}"
    def outcome_file = "${job_name.replace((char) '/', (char) '_')}-outcome.csv"
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

    if (info.has_min_requirements) {
        extra_setup_code += """
python3 -m venv --system-site-packages --without-pip venv
export PATH="\$PWD/venv/bin:\$PATH"
python3 scripts/min_requirements.py ${info.python_requirements_override_file}
"""
    }

    return instrumented_node_job(node_label, job_name) {
        try {
            deleteDir()
            if (use_docker) {
                common.get_docker_image(platform)
            }
            dir('src') {
                checkout_repo.checkout_repo(info)
                writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
ulimit -f 20971520
export MBEDTLS_TEST_OUTCOME_FILE='$outcome_file'
${extra_setup_code}
./tests/scripts/all.sh --seed 4 --keep-going $component
"""
                sh 'chmod +x steps.sh'
            }
            timeout(time: common.perJobTimeout.time,
                    unit: common.perJobTimeout.unit) {
                try {
                    if (use_docker) {
                        analysis.record_inner_timestamps(node_label, job_name) {
                            if (common.is_open_ci_env && platform.startsWith('arm-compilers')) {
                                withCredentials([string(credentialsId: 'MBEDTLS_ARMCLANG_UBL_CODE', variable:'MBEDTLS_ARMCLANG_UBL_CODE')]) {
                                    sh common.docker_script(
                                        platform,
                                        '/bin/sh',
                                        '-c \'exec $ARMC6_BIN_DIR/armlm activate -code "$MBEDTLS_ARMCLANG_UBL_CODE"\'',
                                        ['MBEDTLS_ARMCLANG_UBL_CODE']
                                    )
                                }
                            }
                            sh common.docker_script(
                                platform, "/var/lib/build/steps.sh"
                            )
                        }
                    } else {
                        dir('src') {
                            analysis.record_inner_timestamps(node_label, job_name) {
                                sh './steps.sh'
                            }
                        }
                    }
                } finally {
                    dir('src') {
                        analysis.stash_outcomes(info, job_name)
                    }
                    dir('src/tests/') {
                        common.archive_zipped_log_files(job_name)
                    }
                }
            }
        } catch (err) {
            info.failed_builds << job_name
            throw (err)
        } finally {
            deleteDir()
        }
    }
}

def gen_windows_testing_job(BranchInfo info, String toolchain, String label_prefix='') {
    def prefix = "${label_prefix}Windows-${toolchain}"
    def build_configs, arches, build_systems, retargeted
    if (toolchain == 'mingw') {
        build_configs = ['mingw']
        arches = ['x64']
        build_systems = ['shipped']
        retargeted = [false]
    } else {
        build_configs = ['Release', 'Debug']
        arches = ['Win32', 'x64']
        build_systems = ['shipped', 'cmake']
        retargeted = [false, true]
    }

    // Generate the test configs we will be testing, and tag each with the group they will be executed in
    def test_configs = [build_configs, arches, build_systems, retargeted].combinations().collect { args ->
        def (build_config, arch, build_system, retarget) = args
        def job_name = "$prefix${toolchain == 'mingw' ? '' : "-$build_config-$arch-$build_system${retarget ? '-retarget' : ''}"}"
        /* Sort debug builds using the cmake build system into individual groups, since they are by far the slowest,
         * lumping everything else into a single group per toolchain. This should give us workgroups that take between
         * 15-30 minutes to execute. */
        def group = build_config == 'Debug' &&  build_system == 'cmake' ? job_name : prefix
        return [
            group:       group,
            job_name:    job_name,
            test_config: [
                visual_studio_configurations:    [build_config],
                visual_studio_architectures:     [arch],
                visual_studio_solution_types:    [build_system],
                visual_studio_retarget_solution: [retarget],
            ],
        ]
    }

    // Return one job per workgroup
    return test_configs.groupBy({ item -> (String) item.group }).collectEntries { group, items ->
        return instrumented_node_job('windows', group) {
            try {
                stage('checkout') {
                    dir("src") {
                        deleteDir()
                        checkout_repo.checkout_tls_repo(info)
                    }
                    /* The empty files are created to re-create the directory after it
                     * and its contents have been removed by deleteDir. */
                    dir("logs") {
                        deleteDir()
                        writeFile file: '_do_not_delete_this_directory.txt', text: ''
                    }

                    dir("worktrees") {
                        deleteDir()
                        writeFile file: '_do_not_delete_this_directory.txt', text: ''
                    }

                    if (info.has_min_requirements) {
                        dir("src") {
                            timeout(time: common.perJobTimeout.time,
                                unit: common.perJobTimeout.unit) {
                                bat "python scripts\\min_requirements.py ${info.python_requirements_override_file}"
                            }
                        }
                    }

                    /* libraryResource loads the file as a string. This is then
                     * written to a file so that it can be run on a node. */
                    def windows_testing = libraryResource 'windows/windows_testing.py'
                    writeFile file: 'windows_testing.py', text: windows_testing
                }

                analysis.record_inner_timestamps('windows', group) {
                    /* Execute each test in a workgroup serially. If any exceptions are thrown store them, and continue
                     * with the next test. This replicates the preexisting behaviour windows_testing.py and
                     * jobs.failFast = false */
                    def exceptions = items.findResults { item ->
                        def job_name = (String) item.job_name
                        try {
                            stage(job_name) {
                                common.report_errors(job_name) {
                                    def extra_args = ''
                                    if (toolchain != 'mingw') {
                                        writeFile file: 'test_config.json', text: JSONObject.fromObject(item.test_config).toString()
                                        extra_args = '-c test_config.json'
                                    }

                                    timeout(time: common.perJobTimeout.time + common.perJobTimeout.windowsTestingOffset,
                                        unit: common.perJobTimeout.unit) {
                                        bat "python windows_testing.py src logs $extra_args -b $toolchain"
                                    }
                                }
                            }
                        } catch (exception) {
                            info.failed_builds << job_name
                            return exception
                        }
                        return null
                    }
                    // If we collected any exceptions, throw the first one
                    if (exceptions.size() > 0) {
                        throw exceptions.first()
                    }
                }
            } finally {
                deleteDir()
            }
        }
    }
}

def gen_windows_jobs(BranchInfo info, String label_prefix='') {
    String preamble = ''
    if (info.has_min_requirements) {
        preamble += "python scripts\\min_requirements.py ${info.python_requirements_override_file} || exit\r\n"
    }

    def jobs = [:]
    jobs = jobs + gen_simple_windows_jobs(
        info, label_prefix + 'win32-mingw',
        preamble + scripts.win32_mingw_test_bat
    )
    jobs = jobs + gen_simple_windows_jobs(
        info, label_prefix + 'win32_msvc12_32',
        preamble + scripts.win32_msvc12_32_test_bat
    )
    jobs = jobs + gen_simple_windows_jobs(
        info, label_prefix + 'win32-msvc12_64',
        preamble + scripts.win32_msvc12_64_test_bat
    )
    for (build in common.get_supported_windows_builds()) {
        jobs = jobs + gen_windows_testing_job(info, build, label_prefix)
    }
    return jobs
}

def gen_abi_api_checking_job(BranchInfo info, String platform, String label_prefix = '') {
    String job_name = "${label_prefix}ABI-API-checking"
    String script_in_docker = '''
tests/scripts/list-identifiers.sh --internal
scripts/abi_check.py -o FETCH_HEAD -n HEAD -s identifiers --brief
'''

    Closure post_checkout = {
        sshagent([env.GIT_CREDENTIALS_ID]) {
            sh "git fetch --depth 1 origin ${CHANGE_TARGET}"
        }
    }

    return gen_docker_job(info, job_name, platform, script_in_docker,
                          post_checkout: post_checkout)
}

def gen_code_coverage_job(BranchInfo info, String platform, String label_prefix='') {
    String job_name = "${label_prefix}code-coverage"
    String script_in_docker = '''
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

    Closure post_success = {
        String coverage_log = readFile('coverage-summary.txt')
        info.coverage_details = coverage_log.substring(
            coverage_log.indexOf('\nCoverage\n') + 1
        )
    }

    return gen_docker_job(info, job_name, platform, script_in_docker,
                          post_success: post_success)
}

/* Mbed OS Example job generation */
def gen_all_example_jobs(BranchInfo info = null) {
    def jobs = [:]

    examples.examples.each { example ->
        if (example.value['should_run'] == 'true') {
            for (compiler in example.value['compilers']) {
                for (platform in example.value['platforms']()) {
                    if (examples.raas_for_platform[platform]) {
                        jobs = jobs + gen_mbed_os_example_job(
                            info,
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

def gen_mbed_os_example_job(BranchInfo info, repo, branch, example, compiler, platform, raas) {
    def jobs = [:]
    def job_name = "mbed-os-${example}-${platform}-${compiler}"

    return instrumented_node_job(compiler, job_name) {
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
                            checkout_repo.checkout_mbed_os(info)
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
                            } catch (AbortException err) {
                                if (attempt == 3) throw (err)
                            }
                        }
                    }
                }
            }
        } catch (err) {
            info.failed_builds << job_name
            throw (err)
        } finally {
            deleteDir()
        }
    }
}

def gen_coverity_push_jobs(BranchInfo info) {
    def jobs = [:]
    def job_name = 'coverity-push'

    if (info.branch == "development") {
        jobs << instrumented_node_job('container-host', job_name) {
            try {
                dir("src") {
                    deleteDir()
                    checkout_repo.checkout_tls_repo(info)
                    sshagent([env.GIT_CREDENTIALS_ID]) {
                        analysis.record_inner_timestamps('container-host', job_name) {
                            // Git complains about non-fast-forward operations when trying to push a shallow commit
                            sh 'git fetch --unshallow && git push origin HEAD:coverity_scan'
                        }
                    }
                }
            } catch (err) {
                info.failed_builds << job_name
                throw (err)
            } finally {
                deleteDir()
            }
        }
    }

    return jobs
}

def gen_release_jobs(BranchInfo info, String label_prefix='', boolean run_examples=true) {
    def jobs = [:]

    if (env.RUN_ALL_SH == "true") {
        info.all_sh_components.each({component, platform ->
            jobs = jobs + gen_all_sh_jobs(info, platform, component, label_prefix)
        })
    }

    if (info.repo == 'tls') {
        if (env.RUN_BASIC_BUILD_TEST == "true") {
            jobs = jobs + gen_code_coverage_job(info, 'ubuntu-16.04-amd64', label_prefix);
        }

        /* FreeBSD all.sh jobs */
        if (env.RUN_FREEBSD == "true") {
            for (platform in common.bsd_platforms) {
                for (component in common.freebsd_all_sh_components) {
                    jobs = jobs + gen_all_sh_jobs(info, platform, component, label_prefix)
                }
            }
        }

        if (env.RUN_WINDOWS_TEST == "true") {
            jobs = jobs + gen_windows_jobs(info, label_prefix)
        }

        if (run_examples) {
            jobs = jobs + gen_all_example_jobs(info)
        }

        if (env.PUSH_COVERITY == "true") {
            jobs = jobs + gen_coverity_push_jobs(info)
        }
    }

    return jobs
}

def gen_dockerfile_builder_job(String platform, boolean overwrite=false) {
    def (image, arch) = platform.split(/-(?=[^-]*$)/)
    def dockerfile = libraryResource "docker_files/$image/Dockerfile"
    def tag = "$image-${common.git_hash_object(dockerfile)}-$arch"
    def cache = "$image-cache-$arch"
    def check_docker_image
    if (common.is_open_ci_env) {
        check_docker_image = "docker manifest inspect $common.docker_repo:$tag > /dev/null 2>&1"
    } else {
        check_docker_image = "aws ecr describe-images --repository-name $common.docker_repo_name --image-ids imageTag=$tag"
    }

    common.docker_tags[platform] = tag

    return job(platform) {
        /* Take the lock on the master node, so we don't tie up an executor while waiting */
        lock(tag) {
            def node_label = arch == 'amd64' ? 'dockerfile-builder' : "container-host-$arch"
            analysis.node_record_timestamps(node_label, platform) {
                def image_exists = false
                if (!overwrite) {
                    image_exists = sh(script: check_docker_image, returnStatus: true) == 0
                }
                if (overwrite || !image_exists) {
                    dir('docker') {
                        deleteDir()
                        try {
                            writeFile file: 'Dockerfile', text: dockerfile
                            def extra_build_args = ''

                            if (common.is_open_ci_env) {
                                withCredentials([string(credentialsId: 'DOCKER_AUTH', variable: 'TOKEN')]) {
                                    sh """\
mkdir -p ${env.HOME}/.docker
cat > ${env.HOME}/.docker/config.json << EOF
{
        "auths": {
                "https://index.docker.io/v1/": {
                        "auth": "\${TOKEN}"
                }
        }
}
EOF
chmod 0600 ${env.HOME}/.docker/config.json
"""
                                }
                            } else {
                                sh """\
aws ecr get-login-password | docker login --username AWS --password-stdin $common.docker_ecr
"""
                            }

                            // Generate download URL for armclang
                            if (platform.startsWith('arm-compilers')) {
                                withCredentials(common.is_open_ci_env ? [] : [aws(credentialsId: 'armclang-readonly-keys')]) {
                                    sh '''
aws s3 presign s3://trustedfirmware-private/armclang/ARMCompiler6.21_standalone_linux-x86_64.tar.gz >armc6_url
'''
                                    extra_build_args +=
                                        ' --secret id=armc6_url,src=./armc6_url'
                                }
                            }

                            analysis.record_inner_timestamps(node_label, platform) {
                                sh """\
# Use BuildKit and a remote build cache to pull only the reuseable layers
# from the last successful build for this platform
DOCKER_BUILDKIT=1 docker build \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    $extra_build_args \
    --cache-from $common.docker_repo:$cache \
    --cache-from $common.docker_repo:$image-cache \
    -t $common.docker_repo:$tag \
    -t $common.docker_repo:$cache \
    - <Dockerfile

# Push the image with its unique tag, as well as the build cache tag
docker push $common.docker_repo:$tag
docker push $common.docker_repo:$cache
"""
                            }
                        } finally {
                            deleteDir()
                        }
                    }
                }
            }
        }
    }
}
