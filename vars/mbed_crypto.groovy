/*
 *  Copyright (c) 2019-2021, Arm Limited, All Rights Reserved
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

def run_crypto_tests() {
    try {
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
            jobs = jobs + gen_jobs.gen_windows_jobs()
        }

        /* All.sh jobs */
        if (env.RUN_ALL_SH == "true") {
            for (component in common.available_all_sh_components['ubuntu-16.04']) {
                jobs = jobs + gen_jobs.gen_all_sh_jobs(
                    'ubuntu-16.04', component
                )
            }
            for (component in (common.available_all_sh_components['ubuntu-18.04'] -
                               common.available_all_sh_components['ubuntu-16.04'])) {
                jobs = jobs + gen_jobs.gen_all_sh_jobs(
                    'ubuntu-18.04', component
                )
            }
        }

        if (env.RUN_ABI_CHECK == "true") {
            jobs = jobs + gen_jobs.gen_abi_api_checking_job('ubuntu-16.04')
        }

        /* Deciding whether to run example jobs is handled within this */
        jobs = jobs + gen_jobs.gen_all_example_jobs()

        jobs.failFast = false
        parallel jobs
        common.maybe_notify_github "Crypto Testing", 'SUCCESS',
                                   'All tests passed'
    } catch (err) {
        echo "Caught: ${err}"
        currentBuild.result = 'FAILURE'
        common.maybe_notify_github "Crypto Testing", 'FAILURE',
                                   'Test failure'
    }
}

/* main job */
def run_pr_job(is_production=true) {
    timestamps {
        common.maybe_notify_github "Pre Test Checks", 'PENDING',
                                   'Checking if all PR tests can be run'
        common.maybe_notify_github "Crypto Testing", 'PENDING',
                                   'In progress'
        common.maybe_notify_github "TLS Testing", 'PENDING',
                                   'In progress'
        stage('pre-test-checks') {
            try {
                environ.set_crypto_pr_environment(is_production)
                common.get_all_sh_components(['ubuntu-16.04', 'ubuntu-18.04'])
                common.check_every_all_sh_component_will_be_run()
                common.maybe_notify_github "Pre Test Checks", 'SUCCESS', 'OK'
            } catch (err) {
                if (env.BRANCH_NAME) {
                    def description = 'Pre Test Checks failed.'
                    if (err.getMessage().contains('Pre Test Checks')) {
                        description = err.getMessage()
                    }
                    common.maybe_notify_github "Pre Test Checks", 'FAILURE',
                                               description
                }
                throw (err)
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
