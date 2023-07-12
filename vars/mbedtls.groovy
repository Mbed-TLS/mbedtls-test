/*
 *  Copyright (c) 2018-2021, Arm Limited, All Rights Reserved
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

import hudson.triggers.TimerTrigger
import org.jenkinsci.plugins.parameterizedscheduler.ParameterizedTimerTriggerCause

def run_tls_tests(label_prefix='') {
    try {
        def jobs = [:]

        jobs = jobs + gen_jobs.gen_release_jobs(label_prefix, false)

        if (env.RUN_ABI_CHECK == "true") {
            jobs = jobs + gen_jobs.gen_abi_api_checking_job('ubuntu-16.04')
        }

        jobs = common.wrap_report_errors(jobs)

        jobs.failFast = false
        analysis.record_inner_timestamps('main', 'run_pr_job') {
            parallel jobs
        }
    } catch (err) {
        def failed_names = gen_jobs.failed_builds.keySet().sort().join(" ")
        echo "Caught: ${err}"
        echo "Failed jobs: ${failed_names}"
        common.maybe_notify_github('FAILURE', "Failures: ${failed_names}")
        throw err
    }
}

/* main job */
def run_pr_job(is_production=true) {
    analysis.main_record_timestamps('run_pr_job') {
        try {
            if (is_production) {
                // Cancel in-flight jobs for the same PR when a new job is launched
                def buildNumber = env.BUILD_NUMBER as int
                if (buildNumber > 1)
                    milestone(buildNumber - 1)
                /* If buildNumber > 1, the following statement aborts all builds
                * whose most-recently passed milestone was the previous milestone
                * passed by this job (buildNumber - 1).
                * After this, it checks to see if a later build has already passed
                * milestone(buildNumber), and if so aborts the current build as well.
                *
                * Because of the order of operations, each build is only responsible
                * for aborting the one directly before it, and itself if necessary.
                * Thus we don't have to iterate over all milestones 1 to buildNumber.
                */
                milestone(buildNumber)

                /* Discarding old builds has to be individually configured for each
                * branch in a multibranch pipeline, so do it from groovy.
                */
                properties([
                    buildDiscarder(
                        logRotator(
                            numToKeepStr: '5'
                        )
                    )
                ])
            }

            common.maybe_notify_github('PENDING', 'In progress')

            common.init_docker_images()

            stage('pre-test-checks') {
                environ.set_tls_pr_environment(is_production)
                common.get_branch_information()
                common.check_every_all_sh_component_will_be_run()
            }
        } catch (err) {
            def description = 'Pre Test Checks failed.'
            if (err.message?.startsWith('Pre Test Checks')) {
                description = err.message
            }
            common.maybe_notify_github('FAILURE', description)
            throw (err)
        }

        try {
            stage('tls-testing') {
                run_tls_tests()
            }
        } finally {
            stage('result-analysis') {
                analysis.analyze_results()
            }
        }

        common.maybe_notify_github('SUCCESS', 'All tests passed')
    }
}

/* main job */
def run_job() {
    run_pr_job()
}

void run_release_job() {
    analysis.main_record_timestamps('run_release_job') {
        try {
            environ.set_tls_release_environment()
            common.init_docker_images()
            stage('tls-testing') {
                def jobs = common.wrap_report_errors(gen_jobs.gen_release_jobs())
                jobs.failFast = false
                try {
                    analysis.record_inner_timestamps('main', 'run_release_job') {
                        parallel jobs
                    }
                } finally {
                    if (currentBuild.rawBuild.causes[0] instanceof ParameterizedTimerTriggerCause ||
                        currentBuild.rawBuild.causes[0] instanceof TimerTrigger.TimerTriggerCause) {
                        common.send_email('Mbed TLS nightly tests', env.MBED_TLS_BRANCH, gen_jobs.failed_builds, gen_jobs.coverage_details)
                    }
                }
            }
        }
        finally {
            stage('result-analysis') {
                analysis.analyze_results()
            }
        }
    }
}
