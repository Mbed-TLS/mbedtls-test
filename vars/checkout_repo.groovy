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

def checkout_repo() {
    def git = null
    def cache = '/tmp/mbedtls-git-cache/mbedtls'
    dir(cache) {
        if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
            git = checkout scm
        } else {
            git = checkout_parametrized_repo(env.MBED_TLS_REPO, env.MBED_TLS_BRANCH)
        }
    }
    checkout_parametrized_repo(git.GIT_URL, git.GIT_BRANCH, cache)
}

def checkout_mbed_os_example_repo(repo, branch) {
    if (env.TARGET_REPO == 'example' && env.CHECKOUT_METHOD == 'scm') {
        checkout scm
    } else {
        checkout_parametrized_repo(repo, branch)
    }
}

Map<String, String> checkout_parametrized_repo(String repo, String branch, String reference=null) {
    return checkout([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [[
                name: 'origin',
                url: repo,
                refspec: '+refs/heads/*:refs/remotes/origin/* +refs/tags/*:refs/tags/* +refs/pull/*:refs/pull/*',
                credentialsId: env.GIT_CREDENTIALS_ID
            ]],
            branches: [[name: branch]],
            extensions: [
                [$class: 'CloneOption', timeout: 60, honorRefspec: true, reference: reference],
                [$class: 'SubmoduleOption', recursiveSubmodules: true],
                [$class: 'LocalBranch', localBranch: '**'],
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
