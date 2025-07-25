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

import hudson.AbortException
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

Map<String, String> try_checkout_from_repos(List<String> maybe_repos, String branch) {
    List<String> repos = maybe_repos.findAll()
    if (repos.size() == 0) {
        throw new IllegalArgumentException("No repos specified")
    }

    int i = 0;
    for (; i < repos.size() - 1; i++) {
        try {
            return checkout(parametrized_repo(repos[i], branch))
        } catch (AbortException e) {
            echo "Cloning $branch from ${repos[i]} failed:\n$e.message\nTrying fallback repo ${repos[i+1]}"
        }
    }

    return checkout_report_errors(parametrized_repo(repos[i], branch))
}

String get_submodule_commit(String working_dir = '.', String submodule) {
    return sh(
        script: "sha=\$(git -C '$working_dir' rev-parse 'HEAD:$submodule') && echo \"\$sha\" || true",
        returnStdout: true
    ).trim()
}

void checkout_framework_repo(BranchInfo info) {
    if (env.TARGET_REPO == 'framework' && env.CHECKOUT_METHOD == 'scm') {
        checkout_report_errors(scm)
    } else {
        def branch = env.FRAMEWORK_BRANCH ?: info.framework_override
        if (env.FRAMEWORK_REPO && branch) {
            echo "Applying framework override ($branch)"
            try_checkout_from_repos([env.FRAMEWORK_REPO, env.FRAMEWORK_FALLBACK_REPO], branch)
        } else {
            String commit = get_submodule_commit('..', 'framework')
            if (commit) {
                echo "Cloning default framework version $commit from $env.FRAMEWORK_REPO"
                try_checkout_from_repos([env.FRAMEWORK_REPO, env.FRAMEWORK_FALLBACK_REPO], commit)
            }
        }
    }
}

void checkout_tf_psa_crypto_repo(BranchInfo info) {
    if (env.TARGET_REPO == 'tf-psa-crypto' && env.CHECKOUT_METHOD == 'scm') {
        checkout_report_errors(scm)
        if (!info.framework_override) {
            if (!isUnix()) {
                throw new IllegalStateException("The first checkout of the framework must be made on a Unix node")
            }
            info.framework_override = get_submodule_commit('framework')
            echo "Setting framework override to commit $info.framework_override"
        }
    } else {
        String branch
        if (info.repo == 'tf-psa-crypto') {
            branch = info.branch
        } else {
            branch = env.TF_PSA_CRYPTO_BRANCH
        }
        if (env.TF_PSA_CRYPTO_REPO && branch) {
            if (info.repo != 'tf-psa-crypto') {
                echo "Applying tf-psa-crypto override ($branch)"
            }
            try_checkout_from_repos([env.TF_PSA_CRYPTO_REPO, env.TF_PSA_CRYPTO_FALLBACK_REPO], branch)
        } else {
            String commit = get_submodule_commit('..', 'tf-psa-crypto')
            if (commit) {
                echo "Cloning default tf-psa-crypto version $commit from $env.TF_PSA_CRYPTO_REPO"
                try_checkout_from_repos([env.TF_PSA_CRYPTO_REPO, env.TF_PSA_CRYPTO_FALLBACK_REPO], commit)
            }
        }
    }

    dir('framework') {
        checkout_framework_repo(info)
    }
}

Map<String, String> checkout_tls_repo(BranchInfo info) {
    if (info.repo != 'tls') {
        throw new IllegalArgumentException("checkout_tls_repo() called with BranchInfo for repo '$info.repo'")
    }

    def scm_config
    if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
        scm_config = scm
    } else {
        scm_config = parametrized_repo(env.MBED_TLS_REPO, info.branch)
    }

    def result = checkout_report_errors(scm_config)

    // Do not attempt to clone tf-psa-crypto if the submodule is not present (mbedtls-3.6)
    if (get_submodule_commit('tf-psa-crypto')) {
        dir('tf-psa-crypto') {
            checkout_tf_psa_crypto_repo(info)
        }
    }

    dir('framework') {
        checkout_framework_repo(info)
    }

    write_overrides(info)
    return result
}

void checkout_repo(BranchInfo info) {
    def stashName = "${info.prefix}stash"
    def needUnstash = true

    try {
        if (!info.stash) {
            lock(resource: "stash-lock/${env.BUILD_TAG}-${stashName}") {
                if (!info.stash) {
                    switch (info.repo) {
                        case 'tls':
                            checkout_tls_repo(info)
                            break
                        case 'tf-psa-crypto':
                            checkout_tf_psa_crypto_repo(info)
                            break
                        default:
                            error("Invalid repo: ${info.repo}")
                    }

                    stash name: stashName, includes: '**/*', useDefaultExcludes: false
                    info.stash = stashName
                    needUnstash = false
                }
            }
        }

        if (needUnstash) {
            unstash info.stash
        }
    } catch (exception) {
        echo "Caught: ${common.stack_trace_to_string(exception)}"
        throw exception
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
    String source_ref, remote_tracking_branch
    // Check if branch is a SHA-1 or SHA-256 commit hash.
    if ((branch.length() == 40 || branch.length() == 64) && branch ==~ /\p{XDigit}*+/) {
        // Use 'detached' as the remote tracking branch's name.
        // This prevents warnings about ambiguous refnames.
        source_ref = branch
        remote_tracking_branch = 'detached'
    } else {
        source_ref = branch.replaceFirst('^((refs/)?remotes/)?origin/', '')
        remote_tracking_branch = branch.replaceFirst('^(refs/)?(heads/|tags/|(remotes/)?origin/)?','')
    }
    return [
        $class: 'GitSCM',
        userRemoteConfigs: [[
            url: repo,
            refspec: "+$source_ref:refs/remotes/origin/$remote_tracking_branch",
            credentialsId: env.GIT_CREDENTIALS_ID
        ]],
        branches: [[name: branch]],
        extensions: [
            [$class: 'CloneOption', timeout: 60, honorRefspec: true, shallow: true],
            [$class: 'SubmoduleOption', disableSubmodules: true],
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
