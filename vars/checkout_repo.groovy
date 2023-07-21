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

void checkout_scm_not_null() {
    if (scm instanceof NullSCM) {
        echo 'scm is NullSCM - branch was deleted while being tested'
        /* Color the stage yellow */
        throw new FlowInterruptedException(Result.UNSTABLE, new CauseOfInterruption[0])
    } else {
        checkout scm
    }
}

def checkout_repo() {
    if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
        checkout_scm_not_null()
    } else {
        checkout_parametrized_repo(MBED_TLS_REPO, MBED_TLS_BRANCH)
    }
}

def checkout_mbed_os_example_repo(repo, branch) {
    if (env.TARGET_REPO == 'example' && env.CHECKOUT_METHOD == 'scm') {
        checkout_scm_not_null()
    } else {
        checkout_parametrized_repo(repo, branch)
    }
}

def checkout_parametrized_repo(repo, branch) {
    checkout([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [[
                url: repo,
                refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull/*:refs/pull/*',
                credentialsId: env.GIT_CREDENTIALS_ID
            ]],
            branches: [[name: branch]],
            extensions: [
                [$class: 'CloneOption', timeout: 60],
                [$class: 'SubmoduleOption', recursiveSubmodules: true],
                [$class: 'LocalBranch', localBranch: branch],
            ],
        ]
    ])
}

def checkout_mbed_os() {
    checkout([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [
                [url: MBED_OS_REPO, credentialsId: env.GIT_CREDENTIALS_ID]
            ],
            branches: [[name: MBED_OS_BRANCH]],
            extensions: [
                [$class: 'CloneOption', timeout: 60, shallow: true],
            ],
        ]
    ])
    if (env.MBED_TLS_BRANCH) {
        dir('features/mbedtls/importer') {
            dir('TARGET_IGNORE/mbedtls')
            {
                deleteDir()
                checkout_mbedtls_repo()
            }
            sh """\
ulimit -f 20971520
make all
"""
        }
    }
}
