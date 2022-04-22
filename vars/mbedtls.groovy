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

def run_tls_tests(label_prefix='') {
    try {
        def jobs = [:]

        jobs = jobs + gen_jobs.gen_release_jobs(label_prefix, false)

        if (env.RUN_ABI_CHECK == "true") {
            jobs = jobs + gen_jobs.gen_abi_api_checking_job('ubuntu-16.04')
        }

        jobs.failFast = false
        parallel jobs
        common.maybe_notify_github "TLS Testing", 'SUCCESS',
                                   'All tests passed'
    } catch (err) {
        def failed_names = gen_jobs.failed_builds.keySet().sort().join(" ")
        echo "Caught: ${err}"
        echo "Failed jobs: ${failed_names}"
        currentBuild.result = 'FAILURE'
        common.maybe_notify_github "TLS Testing", 'FAILURE',
                                   "Failures: ${failed_names}"
        throw (err)
    }
}

/* main job */
def run_pr_job(is_production=true) {
    timestamps {
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

        /* During the nightly branch indexing, if a target branch has been
         * updated, new merge jobs are triggered for each PR to that branch.
         * If a PR hasn't been updated recently enough, don't run the merge
         * job for that PR.
         */
        if (env.BRANCH_NAME ==~ /PR-\d+-merge/ &&
            currentBuild.rawBuild.getCauses()[0].toString().contains('BranchIndexingCause'))
        {
            upd_timestamp_ms = pullRequest.updatedAt.getTime()
            now_timestamp_ms = currentBuild.startTimeInMillis
            /* current threshold is 7 days */
            long threshold_ms = 7L * 24L * 60L * 60L * 1000L
            if (now_timestamp_ms - upd_timestamp_ms > threshold_ms) {
                error('Not running: PR has not been updated recently enough.')
            }
        }

        common.maybe_notify_github "Pre Test Checks", 'PENDING',
                                   'Checking if all PR tests can be run'
        common.maybe_notify_github "TLS Testing", 'PENDING',
                                   'In progress'
        common.maybe_notify_github "Result analysis", 'PENDING',
                                   'In progress'

        common.init_docker_images()

        stage('pre-test-checks') {
            try {
                environ.set_tls_pr_environment(is_production)
                common.get_branch_information()
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
            stage('tls-testing') {
                run_tls_tests()
            }
        } finally {
            analysis.analyze_results_and_notify_github()
        }
    }
}

/* main job */
def run_job() {
    run_pr_job()
}
