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


import hudson.model.Cause
import hudson.model.Result
import hudson.triggers.TimerTrigger
import jenkins.model.CauseOfInterruption
import org.jenkinsci.plugins.parameterizedscheduler.ParameterizedTimerTriggerCause
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import org.mbed.tls.jenkins.BranchInfo

void run_tls_tests(BranchInfo info, String label_prefix='') {
    try {
        def jobs = [:]

        jobs = jobs + gen_jobs.gen_release_jobs(info, label_prefix, false)

        if (env.RUN_ABI_CHECK == "true") {
            jobs = jobs + gen_jobs.gen_abi_api_checking_job(info, 'ubuntu-18.04-amd64')
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
void run_pr_job(boolean is_production, String branch) {
    analysis.main_record_timestamps('run_pr_job') {
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

        environ.set_tls_pr_environment(is_production)
        boolean is_merge_queue = env.BRANCH_NAME ==~ /gh-readonly-queue\/.*/

        if (!is_merge_queue && currentBuild.rawBuild.getCause(Cause.UserIdCause) == null) {
            if (!common.pr_author_has_write_access("$env.GITHUB_ORG/$env.GITHUB_REPO", env.CHANGE_ID as int)) {
                echo 'PR author not found on allowlist - not building'
                throw new FlowInterruptedException(Result.NOT_BUILT, new CauseOfInterruption[0])
            }
        }

        BranchInfo info

        try {
            common.maybe_notify_github('PENDING', 'In progress')

            if (common.is_open_ci_env && is_merge_queue) {
                // Fake required checks that don't run in the merge queue
                def skipped_checks = [
                    'DCO',
                    'docs/readthedocs.org:mbedtls-versioned',
                    'Travis CI - Pull Request',
                ]
                for (check in skipped_checks) {
                    common.maybe_notify_github('SUCCESS', 'Check passed on PR-head', check)
                }
            }

            common.init_docker_images()

            stage('pre-test-checks') {
                info = common.get_branch_information(branch)
                common.check_every_all_sh_component_will_be_run(info)
            }
        } catch (err) {
            def description = 'Pre-test checks failed.'
            if (err.message?.startsWith('Pre-test checks')) {
                description = err.message
            }
            common.maybe_notify_github('FAILURE', description)
            throw (err)
        }

        try {
            stage('tls-testing') {
                run_tls_tests(info)
            }
        } finally {
            stage('result-analysis') {
                analysis.analyze_results(info)
            }
        }

        common.maybe_notify_github('SUCCESS', 'All tests passed')
    }
}

/* main job */
def run_job() {
    run_pr_job(true, env.CHANGE_BRANCH)
}

void run_release_job(String branch) {
    BranchInfo info
    analysis.main_record_timestamps('run_release_job') {
        try {
            environ.set_tls_release_environment()
            common.init_docker_images()
            stage('branch-info') {
                info = common.get_branch_information(branch)
            }
            try {
                stage('tls-testing') {
                    def jobs = common.wrap_report_errors(gen_jobs.gen_release_jobs(info))
                    jobs.failFast = false
                    analysis.record_inner_timestamps('main', 'run_release_job') {
                        parallel jobs
                    }
                }
            }
            finally {
                stage('result-analysis') {
                    analysis.analyze_results(info)
                }
            }
        } finally {
            stage('email-report') {
                if (currentBuild.rawBuild.causes[0] instanceof ParameterizedTimerTriggerCause ||
                    currentBuild.rawBuild.causes[0] instanceof TimerTrigger.TimerTriggerCause) {
                    common.send_email('Mbed TLS nightly tests', branch, gen_jobs.failed_builds, gen_jobs.coverage_details)
                }
            }
        }
    }
}
