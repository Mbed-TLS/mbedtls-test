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

void checkout_repo(boolean merge=false) {
    if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
        if(merge) {
            echo "$scm.userRemoteConfigs"
            String branch = "PR-$env.CHANGE_ID-merge"
            checkout_parametrized_repo(
                scm.userRemoteConfigs[0].url, branch,
                "+refs/pull/$env.CHANGE_ID/merge:refs/remotes/origin/$branch")
        } else {
            checkout scm
        }
    } else {
        checkout_parametrized_repo(MBED_TLS_REPO, MBED_TLS_BRANCH)
    }
}

def checkout_mbed_os_example_repo(repo, branch) {
    if (env.TARGET_REPO == 'example' && env.CHECKOUT_METHOD == 'scm') {
        checkout scm
    } else {
        checkout_parametrized_repo(repo, branch)
    }
}

void checkout_parametrized_repo(String repo, String branch, String refspec='+refs/heads/*:refs/remotes/origin/*') {
    checkout([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [[
                url: repo,
                refspec: refspec,
                credentialsId: common.git_credentials_id
            ]],
            branches: [[name: branch]],
            extensions: [
                [$class: 'CloneOption', timeout: 60, honorRefspec: true],
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
                [url: MBED_OS_REPO, credentialsId: common.git_credentials_id]
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
