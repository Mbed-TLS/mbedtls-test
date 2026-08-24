Mbed TLS continuous integration (CI) scripts developer's guide
==============================================================

This document offers some guidance to developers of the Mbed TLS continuous integration (CI) scripts. It is intended for Mbed TLS maintainers. The Mbed TLS CI scripts are not intended for external contributions, and many processes in this document are only available to TrustedFirmware members or Arm employees.

## Overview of the Mbed TLS CI

The Mbed TLS CI is expressed as [Jenkins](https://www.jenkins.io/) pipelines written in [Groovy](https://groovy-lang.org/).

The [`mbedtls-test` repository](https://github.com/Mbed-TLS/mbedtls-test) contains:

* Groovy pipeline scripts under [`vars`](vars/), which can import packages under [`src`](src/) (we use the namespace [`org.mbed.tls.jenkins`](src/org/mbed/tls/jenkins/)).
* Docker files used for testing on Linux under [`resources/docker_files`](resources/docker_files/).
* A script used for testing on Windows: [`resources/windows/windows_testing.py`](resources/windows/windows_testing.py).

### Jenkins instance

The Jenkins instance is a service which is known as [OpenCI](https://ci.trustedfirmware.org/view/Mbed-TLS/).

It is maintained by Arm ([private issue board: OSSDEVOPS](https://jira.arm.com/projects/OSSDEVOPS)) on behalf of TrustedFirmware. The OpenCI instance is public. Only TrustedFirmware members and partners can have accounts (access is via [the `trusted-firmware-mbed-tls-openci-users` team in `trusted-firmware-ci` on GitHub](https://github.com/orgs/trusted-firmware-ci/teams/trusted-firmware-mbed-tls-openci-users/members)), but everyone can see test results.

Jobs whose name contains `restricted` are not visible publicly. They are mostly used to test security fixes that are not yet public.

There is a companion [staging](https://ci.staging.trustedfirmware.org/) instance which is sometimes used to test proposed code or configuration changes.

Some old documents and logs reference other Jenkins instances: a Linaro-maintained instance of OpenCI, and an Arm internal CI. Those no longer exist since January 2026.

#### Jenkins jobs

On OpenCI, the jobs are defined by YAML configuration files managed in a Gerrit instance: [browse code](https://review.trustedfirmware.org/plugins/gitiles/ci/mbedtls/mbed-tls-job-configs), [contributor setup](https://review.trustedfirmware.org/Documentation/user-upload.html), [reviews](https://review.trustedfirmware.org/q/project:ci/mbedtls/mbed-tls-job-configs+status:open).

The main jobs on OpenCI are:

* [`mbed-tls-framework-multibranch`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-framework-multibranch/): invoked automatically on pull requests in the [`mbedtls-framework` repository](https://github.com/Mbed-TLS/mbedtls-framework).
* [`mbed-tls-nightly-tests`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-nightly-tests/): invoke daily on each maintained branch.
* [`mbed-tls-pr-head`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-pr-head/), [`mbed-tls-pr-merge`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-pr-merge/): invoked on pull requests in the [`mbedtls` repository](https://github.com/Mbed-TLS/mbedtls). See [“Groovy entry points”](#groovy-entry-points) below. These jobs are meant to be triggered from GitHub. If you want to run them manually on an arbitrary branch, use [`mbed-tls-restricted-pr-test-parametrized`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-restricted-pr-test-parametrized/).
* [`mbedtls-restricted-release-new`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbedtls-restricted-release-new/): run the full release job on a given branch.
* [`mbed-tls-tf-psa-crypto-multibranch`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-tf-psa-crypto-multibranch/): invoked automatically on pull requests in the [`TF-PSA-Crypto` repository](https://github.com/Mbed-TLS/TF-PSA-Crypto).
* `ci-testing` jobs are meant for testing changes to the CI scripts. See [“Validation tools”](#validation-tools) below.

#### Triggering jobs on Jenkins

For security reasons, the CI does not run on pull requests from untrusted users.

At the time of writing, only users with write permissions on the repository are trusted to have the CI run automatically on their pull requests. The restriction is implemented in `pr_author_has_write_access()` in `vars/common.groovy`.

There is a separate access control list for triggering CI jobs manually: this is allowed for users in the [`mbed-tls-users` team](https://github.com/orgs/trusted-firmware-ci/teams/mbed-tls-users) in the `trusted-firmware-ci` GitHub organization, if they have an account on Jenkins that's tied to their GitHub account.

### CI platforms

The following table lists the platforms on which project tests can run, as well as the version of some tools.

| OS | Version | Arch | Git | Python | Perl | CMake | GNU Make | Native C compilers | Last checked | Notes |
| -- | ------- | ---- | --- | ------ | ---- | ----- | -------- | ------------------ | ------------ | ----- |
| FreeBSD | 14.3 | amd64 | 2.54.0 | `python3` 3.12; `python3.11` | 5.42.2 | 3.31.12 | `gmake` 4.4.1 | Clang 19 | 2026-08-24 | |
| Ubuntu | 16.04 | amd64 | 2.7.4 | `python3` 3.8; `python3.5`, `python3.6` | 5.22.1 | 3.20.2; `/usr/bin/cmake` 3.5.1; `cmake-3.10.2` | 4.1 | `gcc` 5.4, `clang` 3.8, `gcc-4.7`, `clang` 3.8, `clang-3.5` | 2026-08-24 | |
| Ubuntu | 16.04 | aarch64 | 2.7.4 | `python3` 3.8; `python3.5`, `python3.6` | 5.22.1 | 3.20.2; `/usr/bin/cmake` 3.5.1; `cmake-3.10.2` | 4.1 | `gcc` 5.4, `clang` 3.8, `gcc-4.7`, `clang` 3.8, `clang-3.5` | 2026-08-24 | |
| Ubuntu | 18.04 | amd64 | 2.17.1 | `python3` 3.8; `python3.6` | 5.26.1 | 3.20.2; `/usr/bin/cmake` 3.10.2; `cmake-3.10.2` | 4.1 | `gcc` 7.5, `clang` 6 | 2026-08-24 | |
| Ubuntu | 18.04 | aarch64 | 2.17.1 | `python3` 3.8; `python3.6` | 5.26.1 | 3.20.2; `/usr/bin/cmake` 3.10.2; `cmake-3.10.2` | 4.1 | `gcc` 7.5, `clang` 6 | 2026-08-24 | |
| Ubuntu | 20.04 | amd64 | 2.25.1 | `python3` 3.8 | 5.30.0 | 3.20.2; `/usr/bin/cmake` 3.16.3; `cmake-3.10.2` | 4.2.1 | `gcc` 9.4 | 2026-08-24 | `arm-compilers` |
| Ubuntu | 24.04 | amd64 | 2.43.0 | `python3` 3.12 | 5.38.2 | 3.28.3; `cmake-3.10.2`; `cmake-3.20.2` | 4.3 | `gcc` 13.3.0, `clang` 18 | 2026-08-24 | |
| Ubuntu | 24.04 | aarch64 | 2.43.0 | `python3` 3.12 | 5.38.2 | 3.28.3; `cmake-3.10.2`; `cmake-3.20.2` | 4.3 | `gcc` 13.3.0, `clang` 18 | 2026-08-24 | |
| Ubuntu | 26.04 | amd64 | 2.53.0 | `python3` 3.14 | 5.40.1 | 4.2.3; `cmake-3.10.2`; `cmake-3.20.2` | 4.4.1 | `gcc` 15.2.0, `gcc-16` (16.2), `clang` 21, `clang-23` | 2026-08-24 | |
| Ubuntu | 26.04 | aarch64 | 2.53.0 | `python3` 3.14 | 5.40.1 | 4.2.3; `cmake-3.10.2`; `cmake-3.20.2` | 4.4.1 | `gcc` 15.2.0, `gcc-16` (16.2), `clang` 21, `clang-23` | 2026-08-24 | |
| Windows | x64 | Server 2016 (10.0.14393) | 2.33.1 | `python` 3.10 | 5.32.1 | 3.21.3 | `gmake` 4.2.1 | MinGW64 `gcc` 6.3; Strawberry `gcc` 8.3; Visual Studio 2017 | 2026-08-24 | |

Linux platforms run in Docker containers. The host is Debian 13 (trixie). The host is accessible to our Groovy code, but it doesn't run any project scripts on the Docker hosts.

## General programming advice

### Compatibility with `mbedtls`

The scripts in `mbedtls-test` must work with:

* The `development` branch of Mbed TLS.
* The `master` branch of Mbed TLS (which only contains releases).
* The `main` branch of the PSA Crypto implementation.
* The latest release, in case we need to issue a patch release.
* Long-time support branches.
* Pull requests targeting one of the above, and more generally branches forked from the above.

Note in particular that `mbedtls-test` must support branches that are somewhat out of date, to avoid disrupting ongoing work. An active pull request that passed the CI at some point should generally not fail due to an upgrade of `mbedtls-test`. The definition of “active” can vary, but generally we want to preserve compatibility for at least a few months, and in any case we want to preserve compatibility with the last release in each maintained branch (in case we need to do an emergency patch release).

The code in `mbedtls-test` knows what branch to test because it is passed as environment variables. The environment variables point to a Git repository and a branch name.

#### Interface transitioning

If you want to change the interface between `mbedtls` and `mbedtls-test`, you need to proceed with caution. This interface is not documented, but crucial to having practical working CI. Any incompatible change must be done gradually.

If you add tests in `mbedtls` that require a new tool on the CI:

1. Make the new tool available. If the tool runs on Linux, add it to the Docker image(s) via a pull request on `mbedtls-test`. If the tool doesn't run on Linux, this will require a request to the Arm devops team that manages the [Jenkins instance](#jenkins-instance).
2. Make a pull request in `mbedtls` that starts using the new tool.

If you add a new entry point in `mbedtls` that CI code should invoke:

1. Open a pull request in `mbedtls` that adds the new entry point. Label it “DO NOT MERGE” for the time being.
2. Open a pull request in `mbedtls-test` that adds code that checks whether the new entry point is present, and runs it if present.
3. Test the `mbedtls-test` code both against `development` (at least) and against the branch of your `mbedtls` pull request.
4. Merge the `mbedtls-test` pull request (once tested and approved).
5. Trigger a new CI run on the `mbedtls` pull request. If that passes (and the pull request is approved), the pull request can be merged.

What goes for `mbedtls` also goes for other repositories tested by `mbedtls-test`, in particular [TF-PSA-Crypto](https://github.com/Mbed-TLS/TF-PSA-Crypto).

## Groovy scripts

### Groovy entry points

The entry points for the Groovy code are scripts in the [`vars`](vars/) directory.

* Release/nightly jobs invoke [`vars/mbedtls-release-Jenkinsfile`](vars/mbedtls-release-Jenkinsfile) which runs all of `all.sh` on Linux, a small subset of `all.sh` on FreeBSD, several Windows jobs, and the test coverage job (`basic-build-test.sh`).
* Pull request (“pr-head” and “pr-merge”) jobs invoke [`mbedtls.run_job()`](vars/mbedtls.groovy). The pr-merge job only runs the “Interface stability tests” (formerly known as “ABI-API-check”). The pr-head job runs a test campaign consisting of `all.sh` on Linux without `release_*` components, the same subset of `all.sh` on FreeBSD as the release job, and a subset of the Windows jobs.

The way the entry point is reached depends on several settings.

* Release/nightly jobs use the `script-path` (“Script Path”) setting in the job configuration to point to a file in the `mbedtls-test` repository.
* Pull request jobs follow the Jenkins “Multibranch Pipeline” template, which use the `script-path` (“Script Path”) setting in the job configuration to point to a file in the default branch of the tested repository. We use [`tests/.jenkins/Jenkinsfile`](https://github.com/Mbed-TLS/mbedtls/blob/master/tests/.jenkins/Jenkinsfile) which just invokes `mbedtls.run_job()` from the repository (`mbedtls-test`) and branch (`main`) in the job properties.

### Jenkins pipeline structure

Jenkins runs a [pipeline](https://www.jenkins.io/doc/book/pipeline/), which is expressed as a series of stages which can themselves have sub-stages executed in parallel or serially. We use [scripted pipelines](https://www.jenkins.io/doc/book/pipeline/#scripted-pipeline-fundamentals).

At runtime, the general structure of the pipeline for a release or PR job is:

1. Set up the Docker images. The images are normally cached in a Docker registry ([`trustedfirmware`](https://hub.docker.com/u/trustedfirmware) on DockerHub, plus an internal cache), but they will be (re)built automatically if needed.
2. Obtain some information about the branch to test. In particular, run `tests/scripts/all.sh --list-all-components` from the tested branch, as well as `tests/scripts/all.sh --list-components` in each Docker container to determine which one to use in the next step.
3. Run all the components to test in parallel. The components consist of:
    * A full run of `all.sh` (spread over multiple Linux versions), invoked by `gen_jobs.gen_all_sh_jobs`.
    * Selected `all.sh` components on FreeBSD (the selection is in `common.freebsd_all_sh_components`).
    * Ad hoc Windows jobs from `scripts.groovy`, invoked by `gen_jobs.gen_windows_jobs`.
    * One or more runs of `resources/windows/windows_testing.py`, invoked by `gen_jobs.gen_windows_testing_job`. The set of runs is determined by `common.get_supported_windows_builds`.
    * A test coverage job (`basic-build-test.sh`). Omitted in the pull request job.
4. Run result analysis (`analysis.analyze_results`). This runs `tests/scripts/analyze_outcomes.py` from the tested branch.

### Miscellaneous Jenkins APIs

#### Build causes

Confused about build causes? Read [Bence's guide](https://github.com/Mbed-TLS/mbedtls-test/pull/129/files#r1348764797).

### Groovy coding tips

#### Available library functions

The Groovy language gives access to the Java standard library. However, on Jenkins, our code runs in a sandbox that blocks large parts of the library.

Jenkins (with the plugins we have installed) makes some extra functions available, in particular [pipeline steps](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/).

The set of Jenkins plugins is managed by the Arm devops team. The list of plugins is public in [`plugins.yaml`](https://gitlab.geo.arm.com/software/eng-infra/oss/tf-openci/cloudbees-bundles/-/blob/main/openci-production-prod/plugins.yaml) and the pinned versions are in [`plugins-catalog.yaml`](https://gitlab.geo.arm.com/software/eng-infra/oss/tf-openci/cloudbees-bundles/-/blob/main/openci-production-prod/plugins-catalog.yaml).

#### Global variables

Note that Groovy does not have global variables as such. Each module (`*.groovy` file) is a class, and that class can have multiple instances. Therefore, avoid using script-scope variables in a Groovy module that is loaded from another module. There's existing code that does this, but it's fragile and has caused us headaches so we are moving away from that.

### Playing well with Jenkins

#### Where your code runs

The entry point of the pipeline runs on the Jenkins master node. Because all jobs start on this node, we should not do much on the master node. In particular, we don't check out the code to test on the master. All computation-heavy or I/O-heavy processing must be performed on an executor:

```
common.mbedtls_node (label) {
    // IO-heavy or computation-heavy code
}
```

The node label identifies a [Jenkins executor](#jenkins-executors).

## Docker images

Most of our Linux testing happens in Docker containers.

### Using Docker locally

See [`resources/docker_files/README.md`](resources/docker_files/README.md).

### Docker container selection

For each `all.sh` component, the Groovy code selects one of the Docker containers that supports that component, based on running `all.sh --list-components` inside that Docker image.

## Jenkins executors

The label identifies what features the executor needs to have. In particular, this encodes the operating system. We use four labels:

* `mbedtls-container-host`, which runs Linux on x86_64 and has Docker. Most of our Linux code runs in Docker containers.
* `mbedtls-container-host-arm64`, similar to `mbedtls-container-host` but running on arm64.
* `mbedtls-freebsd`
* `mbedtls-windows`

The executors (“AMIs”) are managed by the Arm devops team. The list of labels is configured in [`jenkins-clouds.yaml`](https://gitlab.geo.arm.com/software/eng-infra/oss/tf-openci/cloudbees-bundles/-/blob/main/openci-production-prod/jenkins-clouds.yaml).
The software running on these executors is configured through descriptions stored in the [aws-amis](https://review.trustedfirmware.org/plugins/gitiles/ci/aws-amis/+/refs/heads/master) repository.

### Jenkins executor troubleshooting job

Arm team members can get shell access to an executor instance through the [EC2 troubleshooting job](https://confluence.arm.com/spaces/CESW/pages/2883785947/User+facing+EC2+Troubleshooting+Job) (Arm internal link).

## Validating changes

There is no continuous integration on the `mbedtls-test` repository (except a DCO check for the rare external contributions). Therefore, whenever you change the code, you must run some test jobs manually. What to run depends on what you're changing.

As discussed in [“Versioning”](#versioning), remember that the `mbedtls-test` repository must work not only with `development`, but also with LTS branches and with older branches.

### Validation tools

To validate changes, first upload your changes to a branch in the `mbedtls-test` repository. (Forks are not supported.) Use your personal namespace, i.e. branches called `dev/${your_github_username}/${some_meaningful_name}`. There are two test jobs that cover the common cases:

* [`mbedtls-release-ci-testing`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbedtls-release-ci-testing/): runs a full CI with a chosen branch of `mbedtls-test` on a chosen commit from any repository. Note that in addition to selecting your `mbedtls-test` branch in the dropdown, you need to check one or more of the boxes selecting what will run (`RUN_xxx` variables), otherwise not much will happen.
* [`mbed-tls-restricted-pr-test-parametrized`](https://ci.trustedfirmware.org/view/Mbed-TLS/job/mbed-tls-restricted-pr-test-parametrized/): runs the PR tests. Useful for what the release job doesn't cover — mainly “Interface stability tests” (formerly known as “ABI-API-check”).

To validate changes to code that's specific to pull requests, such as GitHub reporting, see [the primary PR CI testing PR](https://github.com/Mbed-TLS/mbedtls-restricted/pull/906) (private link).

### Testing new Jenkins executor images

To validate a new Jenkins executor image:

1. Make a merge request for the new AMI on https://review.trustedfirmware.org/c/ci/aws-amis .
2. Ask in Arm Slack `#help-oss-devops` for someone from Devops to run the CI. (That's the AMI CI, not to be confused with the Mbed TLS CI.)
3. The new image will be available as “candidate” on the [Jenkins executor troubleshooting job](#jenkins-executor-troubleshooting-job).
4. Once you're happy with the new image, ask Devops to merge the merge request, then to [promote it to production](https://confluence.arm.com/spaces/CESW/pages/2844183494/OpenCI+AMI+Build+and+Promotion+Flow).

### Validation tips

#### Validating Dockerfile changes

If you remove anything, make sure to test with LTS branches. Usually we don't reduce test requirements between major releases, so if test tools are good enough for `development`, they're also good enough for older branches targeting `development` or previous minor releases. But a tool might be used e.g. for 2.28 even if it's unused after 3.0.

If you want to validate a Docker image on the official Docker host (rarely needed):

1. Start a [Jenkins executor troubleshooting job](#jenkins-executor-troubleshooting-job) on `mbedtls-container-host`. Use the `latest` image for what is currently in production, or `candidate` for the last executor CI run (see “[Testing new Jenkins executor images](#testing-new-jenkins-executor-images)”).
2. Get shell access to the troubleshooting job as described in “[Jenkins executor troubleshooting job](#jenkins-executor-troubleshooting-job)”.
3. Run the following commands, replacing `$image` with the image you actually want (e.g. `ubuntu-24.04-fd5b3a5ddc0674c0630639190fb62cfe5c6c5317-amd64`):

    ```
    aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin 211125306678.dkr.ecr.eu-west-1.amazonaws.com
    sudo HOME=$HOME docker pull 211125306678.dkr.ecr.eu-west-1.amazonaws.com/docker.io/trustedfirmware/ci-amd64-mbed-tls-ubuntu:$image
    sudo docker run -u 1000:1000 -e MAKEFLAGS -e VERBOSE_LOGS --rm -i -t -w /var/lib/build -v /home/admin/workspace/mbedtls-restricted-release-ci-testing/src:/var/lib/build -v /opt/host:/opt/host:ro --sysctl net.ipv6.conf.all.disable_ipv6=1 --cap-add SYS_PTRACE 211125306678.dkr.ecr.eu-west-1.amazonaws.com/docker.io/trustedfirmware/ci-amd64-mbed-tls-ubuntu:$image
    ```

#### Validating Groovy changes

Groovy is, for practical purposes, an interpreted language. Things like undefined variables may not be detected until the block of code referencing that variable is executed. As a consequence, test your code even after small changes — there's no compiler to tell you that you misspelled a variable.

#### Validating error reporting

If you make changes that affect error reporting, make sure that failures are still caught properly. We don't want to accidentally make a change that is fine if the tests pass, but hide failures!

There are pull requests for testing various kinds of failures in the [`mbedtls-restricted` repository](https://github.com/Mbed-TLS/mbedtls-restricted/labels/ci-testing) (private link). See [“CI testing: development, good”](https://github.com/Mbed-TLS/mbedtls-restricted/pull/906) for more information.

