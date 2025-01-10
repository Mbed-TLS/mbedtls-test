/* Miscellaneous constants and helper functions
 *
 * Do not define mutable variables as fields! A Groovy module can be
 * instantiated more than once and each instance has its own copy of
 * the file-scope variables. It's ok to have variables that are
 * initialized dynamically (for example, from environment variables)
 * but you need to make sure that the variable will always end up with
 * the same value in a given run.
 */

/*
 *  Copyright (c) 2019-2022, Arm Limited, All Rights Reserved
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

import java.security.MessageDigest
import java.util.concurrent.Callable

import groovy.transform.Field

import com.cloudbees.groovy.cps.NonCPS
import hudson.AbortException
import org.jenkinsci.plugins.github_branch_source.Connector
import org.kohsuke.github.GHPermissionType

import org.mbed.tls.jenkins.BranchInfo

/* Indicates if CI is running on Open CI (hosted on https://ci.trustedfirmware.org/) */
@Field is_open_ci_env = env.JENKINS_URL ==~ /\S+(trustedfirmware)\S+/

/*
 * This controls the timeout each job has. It does not count the time spent in
 * waiting queues and setting up the environment.
 *
 * Raas has its own resource queue with the timeout of 1000s, we need to take
 * it into account for the on-target test jobs.
 */
@Field perJobTimeout = [
        time: 240,
        raasOffset: 17,
        windowsTestingOffset: -60,
        unit: 'MINUTES'
]

@Field compiler_paths = [
    'gcc' : 'gcc',
    'gcc48' : '/usr/local/bin/gcc48',
    'clang' : 'clang',
    'cc' : 'cc'
]

@Field docker_repo_name = is_open_ci_env ? 'ci-amd64-mbed-tls-ubuntu' : 'jenkins-mbedtls'
@Field docker_ecr = is_open_ci_env ? "trustedfirmware" : "666618195821.dkr.ecr.eu-west-1.amazonaws.com"
@Field docker_repo = "$docker_ecr/$docker_repo_name"

/* List of Linux platforms. When a job can run on multiple Linux platforms,
 * it runs on the first element of the list that supports this job. */
@Field final List<String> linux_platforms =
    ['ubuntu-16.04-amd64', 'ubuntu-18.04-amd64', 'ubuntu-20.04-amd64', 'ubuntu-22.04-amd64', 'arm-compilers-amd64',
                           'ubuntu-18.04-arm64', 'ubuntu-20.04-arm64', 'ubuntu-22.04-arm64']
/* List of BSD platforms. They all run freebsd_all_sh_components. */
@Field bsd_platforms = ["freebsd"]

@Field freebsd_all_sh_components = [
    /* Do not include any components that do TLS system testing, because
     * we don't maintain suitable versions of OpenSSL and GnuTLS on
     * secondary platforms. */
    'test_default_out_of_box',          // out of box, make
    /* FreeBSD on the CI doesn't have gcc. */
    'test_clang_opt',                   // clang, make
    //'test_gcc_opt',                     // gcc, make
    'test_cmake_shared',                // cmake
    'test_cmake_out_of_source',         // cmake
]

/* Maps platform names to the tag of the docker image used to test that platform.
 * Populated by init_docker_images() / gen_jobs.gen_dockerfile_builder_job(platform). */
@Field static docker_tags = [:]

/* Compute the git object ID of the Dockerfile.
* Equivalent to the `git hash-object <file>` command. */
@NonCPS
def git_hash_object(str) {
    def sha1 = MessageDigest.getInstance('SHA1')
    sha1.update("blob ${str.length()}\0".bytes)
    def digest = sha1.digest(str.bytes)
    return String.format('%040x', new BigInteger(1, digest))
}


def get_docker_tag(platform) {
    def tag = docker_tags[platform]
    if (tag == null)
        throw new NoSuchElementException(platform)
    else
        return tag
}

@NonCPS
static String stack_trace_to_string(Throwable t) {
    StringWriter writer = new StringWriter()
    PrintWriter printWriter = new PrintWriter(writer)
    t.printStackTrace(printWriter)
    printWriter.close()
    return writer.toString()
}

def <V> V report_errors(String job_name, Callable<V> body) {
    try {
        return body()
    } catch (err) {
        echo """\
Failed job: $job_name
Caught: ${stack_trace_to_string(err)}
"""
        if (!currentBuild.resultIsWorseOrEqualTo('FAILURE')) {
            currentBuild.result = 'FAILURE'
            maybe_notify_github('FAILURE', "Failures: ${job_name}…")
        }
        throw err
    }
}

Map<String, ? super Closure> wrap_report_errors(Map<String, ?> jobs) {
    return (Map<String, ? super Closure>) jobs.collectEntries { key, value ->
        return [(key): value instanceof Callable ? { report_errors(key, value) } : value]
    }
}

String construct_python_requirements_override() {
    List<String> overrides = []

    /* Workaround for https://github.com/Mbed-TLS/mbedtls/issues/8250
     * Affects 3.x branches older than 2023-09-25.
     *
     * The release of `types-jsonschema 4.19.0.0 broke our CI for two reasons:
     * - It drops compatibility with Python 3.5 (which is fair, that's long
     *   out of support), but fails to declare it. As a result,
     *   check_python_files breaks.
     * - Its prebuilt wheels require a recent enough version of pip. Our
     *   FreeBSD instances on OpenCI have a version of pip that's too old.
     *
     * Workaround from https://github.com/Mbed-TLS/mbedtls/pull/8249:
     * Pin an older types-jsonschema.
     */
    if (fileExists('scripts/driver.requirements.txt')) {
        String contents = readFile('scripts/driver.requirements.txt')
        if (contents.contains("\ntypes-jsonschema\n")) {
            /* Use a >= requirement, which min_requirements.py will
             * convert to an == requirement. */
            overrides.add('types-jsonschema >= 3.2.0')
        }
    }

    if (overrides) {
        List<String> header = ['-r scripts/ci.requirements.txt']
        List<String> footer = [''] // to get a trailing newline
        return (header + overrides + footer).join('\n')
    } else {
        return ''
    }
}


def init_docker_images() {
    stage('init-docker-images') {
        def jobs = wrap_report_errors(linux_platforms.collectEntries {
            platform -> gen_jobs.gen_dockerfile_builder_job(platform)
        })
        jobs.failFast = false
        parallel jobs
    }
}

def get_docker_image(platform) {
    def docker_image = get_docker_tag(platform)
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            if (is_open_ci_env)
                sh """\
docker pull $docker_repo:$docker_image
"""
            else
                sh """\
aws ecr get-login-password | docker login --username AWS --password-stdin $docker_ecr
docker pull $docker_repo:$docker_image
"""
            break
        } catch (AbortException err) {
            if (attempt == 3) throw (err)
        }
    }
}

String docker_script(
    String platform,
    String entrypoint,
    String entrypoint_arguments='',
    Iterable<String> env_vars=[]
) {
    def docker_image = get_docker_tag(platform)
    def env_args = env_vars.collect({ e -> "-e $e" }).join(' ')
    return """\
docker run -u \$(id -u):\$(id -g) -e MAKEFLAGS -e VERBOSE_LOGS $env_args --rm --entrypoint $entrypoint \
    -w /var/lib/build -v `pwd`/src:/var/lib/build \
    --cap-add SYS_PTRACE $docker_repo:$docker_image $entrypoint_arguments
"""
}

/* Gather information about the branch that determines how to set up the
 * test environment.
 * In particular, get components of all.sh for Linux platforms. */
List<BranchInfo> get_branch_information(Collection<String> branches) {
    List<BranchInfo> infos = []
    Map<String, Object> jobs = [:]

    branches.each { String branch ->
        BranchInfo info = new BranchInfo()
        info.branch = branch
        infos << info

        String prefix = branches.size() > 1 ? "$branch-" : ''
        jobs << gen_jobs.job(prefix + 'all-platforms') {
            node('container-host') {
                try {
                    // Log the environment for debugging purposes
                    sh script: 'export'

                    dir('src') {
                        deleteDir()
                        checkout_repo.checkout_tls_repo(branch)

                        info.has_min_requirements = fileExists('scripts/min_requirements.py')

                        if (info.has_min_requirements) {
                            info.python_requirements_override_content = construct_python_requirements_override()
                            if (info.python_requirements_override_content) {
                                info.python_requirements_override_file = 'override.requirements.txt'
                            }
                        }
                    }

                    String platform = linux_platforms[0]
                    get_docker_image(platform)
                    def all_sh_help = sh(
                        script: docker_script(
                            platform, "./tests/scripts/all.sh", "--help"
                        ),
                        returnStdout: true
                    )
                    if (all_sh_help.contains('list-components')) {
                        def all = sh(
                            script: docker_script(
                                platform, "./tests/scripts/all.sh",
                                "--list-all-components"
                            ),
                            returnStdout: true
                        ).trim().split('\n')
                        echo "All all.sh components: ${all.join(" ")}"
                        return all.collectEntries { element ->
                            return [(element): null]
                        }
                    } else {
                        error('Pre Test Checks failed: Base branch out of date. Please rebase')
                    }
                } finally {
                    deleteDir()
                }
            }
        }

        linux_platforms.each { platform ->
            jobs << gen_jobs.job(prefix + platform) {
                node(gen_jobs.node_label_for_platform(platform)) {
                    try {
                        dir('src') {
                            deleteDir()
                            checkout_repo.checkout_tls_repo(branch)
                        }
                        get_docker_image(platform)
                        def all_sh_help = sh(
                            script: docker_script(
                                platform, "./tests/scripts/all.sh", "--help"
                            ),
                            returnStdout: true
                        )
                        if (all_sh_help.contains('list-components')) {
                            def available = sh(
                                script: docker_script(
                                    platform, "./tests/scripts/all.sh", "--list-components"
                                ),
                                returnStdout: true
                            ).trim().split('\n')
                            echo "Available all.sh components on ${platform}: ${available.join(" ")}"
                            return available.collectEntries { element ->
                                return [(element): platform]
                            }
                        } else {
                            error('Pre Test Checks failed: Base branch out of date. Please rebase')
                        }
                    } finally {
                        deleteDir()
                    }
                }
            }
        }
    }

    jobs.failFast = true
    def results = (Map<String, Map<String, String>>) parallel(jobs)

    infos.each { BranchInfo info ->
        String prefix = infos.size() > 1 ? "$info.branch-" : ''

        info.all_all_sh_components = results[prefix + 'all-platforms']
        linux_platforms.reverseEach { platform ->
            info.all_all_sh_components << results[prefix + platform]
        }

        if (env.JOB_TYPE == 'PR') {
            // Do not run release components in PR jobs
            info.all_all_sh_components = info.all_all_sh_components.findAll {
                component, platform -> !component.startsWith('release')
            }
        }
    }
    return infos
}

void check_every_all_sh_component_will_be_run(Collection<BranchInfo> infos) {
    Map<String, Collection<String>> untested_all_sh_components = infos.collectEntries { info ->
        def components = info.all_all_sh_components.findResults {
            name, platform -> platform ? null : name
        }
        return components ? [(info.branch): components] : [:]
    }

    if (untested_all_sh_components) {
        def error_lines = ['Pre-test checks failed: Unable to run all.sh components:']
        untested_all_sh_components.collect(
            error_lines,
            { branch, components ->
                String prefix = infos.size() > 1 ? "$branch: " : ''
                return prefix + components.join(',')
            }
        )
        error(error_lines.join('\n'))
    }
}

def get_supported_windows_builds() {
    def vs_builds
    if (env.JOB_TYPE == 'PR') {
        if (env.CHANGE_TARGET == 'mbedtls-2.28') {
            vs_builds = ['2013']
        } else {
            vs_builds = ['2017']
        }
    } else {
        if (env.MBED_TLS_BRANCH == 'mbedtls-2.28') {
            vs_builds = ['2013', '2015', '2017']
        } else {
            vs_builds = ['2017']
        }
    }
    echo "vs_builds = ${vs_builds}"
    return ['mingw'] + vs_builds
}

/* In the PR job (recognized because we set the BRANCH_NAME environment
 * variable), report an additional context to GitHub.
 * Do nothing from a job that isn't triggered from GitHub.
 *
 * state: one of 'PENDING', 'SUCCESS' or 'FAILURE' (case-insensitive).
 *        Contexts used in a CI job should be marked as PENDING at the
 *        beginning of job and as SUCCESS or FAILURE once the outcome is known.
 * description: a free-form description shown next to the state. It is
 *              truncated to 140 characters (GitHub limitation).
 * context (optional): a short string identifying which part of the job this is
 *                     a status for. GitHub only shows the latest state and
 *                     description for a given context. If it is omitted, this
 *                     method determines the correct context from is_open_ci_env
 *                     and BRANCH_NAME.
 */
void maybe_notify_github(String state, String description, String context=null) {
    if (!env.BRANCH_NAME) {
        return;
    }

    /* Truncate the description. Otherwise githubNotify fails. */
    final MAX_DESCRIPTION_LENGTH = 140
    if (description.length() > MAX_DESCRIPTION_LENGTH) {
        description = description.take(MAX_DESCRIPTION_LENGTH - 1) + '…'
    }

    if (context == null) {
        def ci = is_open_ci_env ? 'TF OpenCI' : 'Internal CI'
        def job = env.BRANCH_NAME ==~ /PR-\d+-merge/ ? 'Interface stability tests' : 'PR tests'
        context = "$ci: $job"
    }

    githubNotify context: context,
                 status: state,
                 description: description,
                /* Set owner and repository explicitly in case the multibranch pipeline uses multiple repos
-                * Needed for testing Github merge queues */
                 account: env.GITHUB_ORG,
                 repo: env.GITHUB_REPO
}

def archive_zipped_log_files(job_name) {
    sh """\
for i in *.log; do
    [ -f "\$i" ] || break
    mv "\$i" "$job_name-\$i"
    xz "$job_name-\$i"
done
"""
    archiveArtifacts(
        artifacts: '*.log.xz',
        fingerprint: true,
        allowEmptyArchive: true
    )
}

void maybe_send_email(String name, Collection<BranchInfo> infos) {
    String branches = infos*.branch.join(',')
    def failed_builds = infos.collectMany { info -> info.failed_builds}
    String coverage_details = infos.collect({info -> "$info.branch:\n$info.coverage_details"}).join('\n\n')

    String emailbody, recipients
    boolean failed = infos.size() == 0 || failed_builds.size() > 0
    if (failed) {
        String failures = failed_builds.join(", ")
        emailbody = """
$coverage_details

Logs: ${env.BUILD_URL}

Failures: ${failures}
"""
        recipients = env.TEST_FAIL_EMAIL_ADDRESS
    } else {
        emailbody = """
$coverage_details

Logs: ${env.BUILD_URL}
"""
        recipients = env.TEST_PASS_EMAIL_ADDRESS
    }
    String subject = ((is_open_ci_env ? "TF Open CI" : "Internal CI") + " ${name} " + \
           (failed ? "failed" : "passed") + "! (branches: ${branches})")
    echo """\
To: $recipients
Subject: $subject

$emailbody
"""
    if (recipients) {
        emailext body: emailbody,
                 subject: subject,
                 to: recipients,
                 mimeType: 'text/plain'
    }
}

@NonCPS
boolean pr_author_has_write_access(String repo_name, int pr) {
    String credentials = is_open_ci_env ? 'mbedtls-github-token' : 'd015f9b1-4800-4a81-86b3-9dbadc18ee00'
    def github = Connector.connect(null, Connector.lookupScanCredentials(currentBuild.rawBuild.parent, null, credentials))
    def repo = github.getRepository(repo_name)
    return repo.getPermission(repo.getPullRequest(pr).user) in [GHPermissionType.ADMIN, GHPermissionType.WRITE]
}
