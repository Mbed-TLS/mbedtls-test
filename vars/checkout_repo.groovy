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

class StaticState {
    static String repo = null
    static String commit = null
    static String parents = null
}

void checkout_repo() {
    echo "repo = $StaticState.repo\ncommit = $StaticState.commit\nparents = $StaticState.parents"
    echo "$scm"
    if (StaticState.commit == null) {
        Map scm_vars
        if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
            scm_vars = checkout scm
            echo scm_vars.toString()
            if (scm_vars.GIT_AUTHOR_EMAIL == 'ce-oss-mbed@arm.com') {
                StaticState.parents = sh(script: 'git rev-parse HEAD^@', returnStdout: true)
                sh 'git bundle create merge-commit.bundle HEAD HEAD^!'
                stash(name: 'merge-commit.bundle', includes: 'merge-commit.bundle')
            }
        } else {
            scm_vars = checkout_parametrized_repo(MBED_TLS_REPO, MBED_TLS_BRANCH)
        }
        StaticState.repo = scm_vars.GIT_URL
        StaticState.commit = scm_vars.GIT_COMMIT
        echo "repo = $StaticState.repo; $scm_vars.GIT_URL"
        echo "commit = $StaticState.commit; $scm_vars.GIT_COMMIT"
    } else {
        checkout_parametrized_repo(StaticState.repo, StaticState.commit, StaticState.parents)
    }
}

void checkout_mbed_os_example_repo(String repo, String branch) {
    if (env.TARGET_REPO == 'example' && env.CHECKOUT_METHOD == 'scm') {
        checkout scm
    } else {
        checkout_parametrized_repo(repo, branch)
    }
}

Map checkout_parametrized_repo(String repo, String branch, String parents = null) {
    Map scm_config = [
        $class: 'GitSCM',
        userRemoteConfigs: [[
            url: repo,
            name: 'origin',
            credentialsId: env.GIT_CREDENTIALS_ID
        ]],
        branches: [[name: branch]],
        extensions: [
            [$class: 'CloneOption', timeout: 60, honorRefspec: true],
            [$class: 'SubmoduleOption', recursiveSubmodules: true],
        ],
    ]

    if (parents != null) {
        unstash('merge-commit.bundle')
        sh 'cat merge-commit.bundle'
        scm_config.branches = []
        scm_config.userRemoteConfigs[0].refspec = parents
        checkout(scm_config)
        sh 'git bundle verify merge-commit.bundle'
        return checkout([
            $class: 'GitSCM',
            userRemoteConfigs: [[
                url: "${pwd()}/merge-commit.bundle",
                name: 'bundle',
                refspec: branch
            ]],
            branches: [[name: branch]],
            extensions: [
                [$class: 'CloneOption', timeout: 60, honorRefspec: true],
                [$class: 'SubmoduleOption', recursiveSubmodules: true],
                [$class: 'LocalBranch', localBranch: branch],
            ],
        ])
    }

    return checkout(scm_config)
}

void checkout_mbed_os() {
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
            sh '''\
ulimit -f 20971520
make all
'''
        }
    }
}
