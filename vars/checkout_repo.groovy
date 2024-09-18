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

import hudson.model.Result
import hudson.scm.NullSCM
import jenkins.model.CauseOfInterruption
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import org.mbed.tls.jenkins.BranchInfo

/* Write some files that override the default behavior.
 *
 * This is intended for older branches where the CI was passing when the
 * branch was made, but is now failing, for example due to an external
 * dependency. If we can, we override the behavior to make it more like
 * the target branch.
 *
 * Use archiveArtifacts to attach injected files as build artifacts that
 * are kept with the build logs. This is necessary to diagnose unexpected
 * behavior during the build and reproduce a build locally.
 */
void write_overrides(BranchInfo info) {
    if (info.python_requirements_override_file &&
        info.python_requirements_override_content) {
        writeFile file:info.python_requirements_override_file,
                  text:info.python_requirements_override_content
        archiveArtifacts artifacts:info.python_requirements_override_file
    }
}

Map<String, String> checkout_report_errors(scm_config) {
    if (scm_config instanceof NullSCM) {
        echo 'scm is NullSCM - branch was deleted while being tested'
        /* Color the stage yellow */
        throw new FlowInterruptedException(Result.UNSTABLE, new CauseOfInterruption[0])
    } else {
        try {
            return checkout(scm_config)
        } catch (exception) {
            echo "Git checkout failed (branch deleted / network error?): ${common.stack_trace_to_string(exception)}"
            throw new FlowInterruptedException(Result.UNSTABLE, new CauseOfInterruption[0])
        }
    }
}

Map<String, String> checkout_repo() {
    def scm_config
    if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
        scm_config = scm
    } else {
        scm_config = parametrized_repo(env.MBED_TLS_REPO, env.MBED_TLS_BRANCH)
    }

    // Use bilingual scripts when manipulating the git config
    def sh_or_bat = isUnix() ? {args -> sh(args)} : {args -> bat(args)}
    // Set global config so its picked up when cloning submodules
    sh_or_bat 'git config --global url.git@github.com:.insteadOf https://github.com/'
    try {
        def result = checkout_report_errors(scm_config)
        // After the clone, replicate it in the local config, so it is effective when running inside docker
        sh_or_bat '''
git config url.git@github.com:.insteadOf https://github.com/ && \
git submodule foreach --recursive git config url.git@github.com:.insteadOf https://github.com/
'''
        return result
    } finally {
        // Clean up global config
        sh_or_bat 'git config --global --unset url.git@github.com:.insteadOf'
    }
}

Map<String, String> checkout_repo(BranchInfo info) {
    Map<String, String> m = checkout_repo()
    write_overrides(info)
    return m
}

Map<String, String> checkout_mbed_os_example_repo(String repo, String branch) {
    def scm_config
    if (env.TARGET_REPO == 'example' && env.CHECKOUT_METHOD == 'scm') {
        scm_config = scm
    } else {
        scm_config = parametrized_repo(repo, branch)
    }
    return checkout_report_errors(scm_config)
}

Map<String, Object> parametrized_repo(String repo, String branch) {
    return [
        $class: 'GitSCM',
        userRemoteConfigs: [[
            url: repo,
            credentialsId: env.GIT_CREDENTIALS_ID
        ]],
        branches: [[name: branch]],
        extensions: [
            [$class: 'CloneOption', timeout: 60, honorRefspec: true, shallow: true],
            [$class: 'SubmoduleOption', recursiveSubmodules: true, parentCredentials: true, shallow: true],
            [$class: 'LocalBranch', localBranch: '**'],
        ],
    ]
}

def checkout_mbed_os() {
    checkout_report_errors([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [
                [url: MBED_OS_REPO, credentialsId: env.GIT_CREDENTIALS_ID]
            ],
            branches: [[name: MBED_OS_BRANCH]],
            extensions: [
                [$class: 'CloneOption', timeout: 60, honorRefspec: true, shallow: true],
            ],
        ]
    ])
    if (env.MBED_TLS_BRANCH) {
        dir('features/mbedtls/importer') {
            dir('TARGET_IGNORE/mbedtls')
            {
                deleteDir()
                checkout_repo()
            }
            sh """\
ulimit -f 20971520
make all
"""
        }
    }
}
