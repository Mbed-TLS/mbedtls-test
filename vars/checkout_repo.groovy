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
import hudson.plugins.git.GitSCM
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

void checkout_framework_repo() {
    if (env.TARGET_REPO == 'framework' && env.CHECKOUT_METHOD == 'scm') {
        checkout_report_errors(scm)
    } else if (env.FRAMEWORK_REPO && env.FRAMEWORK_BRANCH) {
        checkout_report_errors(parametrized_repo(env.FRAMEWORK_REPO, env.FRAMEWORK_BRANCH))
    } else {
        echo 'Using default framework version'
    }
}

void checkout_tf_psa_crypto_repo() {
    if (env.TARGET_REPO == 'tf-psa-crypto' && env.CHECKOUT_METHOD == 'scm') {
        checkout_report_errors(scm)
    } else if (env.TF_PSA_CRYPTO_REPO && env.TF_PSA_CRYPTO_BRANCH) {
        checkout_report_errors(parametrized_repo(env.TF_PSA_CRYPTO_REPO, env.TF_PSA_CRYPTO_BRANCH))
    } else {
        echo 'Using default tf-psa-crypto version'
    }

    dir('framework') {
        checkout_framework_repo()
    }
}

Map<String, String> checkout_tls_repo(String branch) {
    def scm_config
    if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
        scm_config = scm
    } else {
        scm_config = parametrized_repo(env.MBED_TLS_REPO, branch)
    }

    // Use bilingual scripts when manipulating the git config
    def sh_or_bat = isUnix() ? {args -> sh(args)} : {args -> bat(args)}
    // Set global config so its picked up when cloning submodules
    sh_or_bat 'git config --global url.git@github.com:.insteadOf https://github.com/'
    try {
        def result = checkout_report_errors(scm_config)

        dir('tf-psa-crypto') {
            checkout_tf_psa_crypto_repo()
        }

        dir('framework') {
            checkout_framework_repo()
        }

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

Map<String, String> checkout_tls_repo(BranchInfo info) {
    if (info.repo != 'tls') {
        throw new IllegalArgumentException("checkout_tls_repo() called with BranchInfo for repo '$info.repo'")
    }
    Map<String, String> m = checkout_tls_repo(info.branch)
    write_overrides(info)
    return m
}

void checkout_repo(BranchInfo info) {
    switch(info.repo) {
        case 'tls':
            checkout_tls_repo(info)
            break
        case 'tf-psa-crypto':
            checkout_tf_psa_crypto_repo()
            break
        default:
            error("Invalid repo: $info.repo")
    }
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

/** Produce an object that can be passed to {@code checkout} to make a shallow clone of the specified branch.
 *
 * @param repo
 *     URL of the Git repo.
 *
 * @param branch
 *     The branch / commit / tag to check out. Supports a variety of formats accepted by
 *     {@code git rev-parse}, eg.:
 *     <ul>
 *         <li>{@code <branchName>}
 *         <li>{@code refs/heads/<branchName>}
 *         <li>{@code origin/<branchName>}
 *         <li>{@code remotes/origin/<branchName>}
 *         <li>{@code refs/remotes/origin/<branchName>}
 *         <li>{@code <tagName>}
 *         <li>{@code refs/tags/<tagName>}
 *         <li>{@code refs/pull/<pullNr>/head}
 *         <li>{@code <commitId>}
 *         <li>{@code ${ENV_VARIABLE}}
 *     </ul>
 *     See also:
 *     <a href="https://www.jenkins.io/doc/pipeline/steps/params/scmgit/#scmgit">
 *     the documentation of the Git Plugin.
 *     </a>
 *
 * @return
 *     A {@link Map} representing a {@link GitSCM} object.
 */
Map<String, Object> parametrized_repo(String repo, String branch) {
    String remoteRef = branch.replaceFirst('^((refs/)?remotes/)?origin/', '')
    String localBranch = branch.replaceFirst('^(refs/)?(heads/|tags/|(remotes/)?origin/)?','')
    return [
        $class: 'GitSCM',
        userRemoteConfigs: [[
            url: repo,
            refspec: "+$remoteRef:refs/remotes/origin/$localBranch",
            credentialsId: env.GIT_CREDENTIALS_ID
        ]],
        branches: [[name: branch]],
        extensions: [
            [$class: 'CloneOption', timeout: 60, honorRefspec: true, shallow: true],
            [$class: 'SubmoduleOption', recursiveSubmodules: true, parentCredentials: true, shallow: true],
            [$class: 'LocalBranch', localBranch: localBranch],
        ],
    ]
}

def checkout_mbed_os(BranchInfo info) {
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
    if (info != null) {
        dir('features/mbedtls/importer') {
            dir('TARGET_IGNORE/mbedtls')
            {
                deleteDir()
                checkout_tls_repo(info)
            }
            sh """\
ulimit -f 20971520
make all
"""
        }
    }
}
