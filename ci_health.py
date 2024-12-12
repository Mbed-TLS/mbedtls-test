#!/usr/bin/python3

"""
Gather indicators of CI health using the Jenkins API over the last week.

Currently two indicators are reported:
    1. Success rate of the nightly jobs. We don't expect "real" failures here,
    so any failure is likely to be an infra issue or a flaky test.
    2. Execution time of PR jobs.

Requires python-jenkins.
(Version 1.4.0-4 from the python3-jenkins package in Ubuntu 24.04 WorksForMe.)

Uses a github token for authentication.
https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens#creating-a-fine-grained-personal-access-token
As of late 2024, I (mpg) used "classic token" with the following permissions:
read:discussion, read:enterprise, read:org, read:project, read:user, user:email
It is likely that a strict subset would work, but I didn't try.
"""

from statistics import quantiles
from datetime import datetime, timedelta
import os
import sys

import jenkins

# References:
# 1. https://python-jenkins.readthedocs.io/
# 2. For each page in the Jenkins web UI, you can append 'api' to the URL (or
# click the "REST API" link on the bottom left of the page) and then click
# "Python" on the resulting page to get a preview of what the corresponding
# API call will return.
#
# I find that (2) is a useful complement to (1) because several Python API
# functions will return a dict but the documention does not tell you what keys
# are present in this dict, which (2) allows you to explore conveniently.
#
# I (mpg) couldn't find a proper _reference_ about the API, to answer
# questions like "what are the possible values for 'result' in a build?".
# Apparently we're expected to guess by looking at examples?
#
# A note about multibranch jobs: there is an extra level of indirection here
# compared to basic jobs, in the that multibranch job will first give you a
# list of "sub-jobs", and the builds are associated to those sub-jobs, no the
# top-level multibranch job.

JENKINS_SERVERS = {
    "Open": "https://mbedtls.trustedfirmware.org/",
    "Internal": "https://jenkins-mbedtls.oss.arm.com/",
}
PR_JOB_NAME = "mbed-tls-pr-head"
NIGHTLY_JOB_NAME = "mbed-tls-nightly-tests"


def gather_durations_ms(server, job_name, since_timestamp_ms):
    """Gather durations of runs started since the given timestamp.

    This function expects a multibranch job and won't work for "basic" jobs.
    The timestamp is in milliseconds since the Unix Epoch.
    The returned durations are in milliseconds.
    """
    durations_ms = []
    for branch in server.get_job_info(job_name)["jobs"]:
        branch_job_name = f"{job_name}/{branch['name']}"
        for build in server.get_job_info(branch_job_name)["builds"]:
            build_info = server.get_build_info(branch_job_name, build["number"])
            if build_info["timestamp"] >= since_timestamp_ms:
                durations_ms.append(build_info["duration"])

    return durations_ms


def gather_statuses(server, job_name, since_timestamp_ms):
    """Gather the number of successes & failures of that job since that date.

    This function expects a "basic" job and won't work for multibranch jobs.
    The timestamp is in milliseconds since the Unix Epoch.
    """
    nb_good, nb_bad = 0, 0
    for build in server.get_job_info(job_name)["builds"]:
        build_info = server.get_build_info(job_name, build["number"])
        if build_info["timestamp"] < since_timestamp_ms:
            continue

        if build_info["result"] == "SUCCESS":
            nb_good += 1
        else:
            nb_bad += 1

    return nb_good, nb_bad


def h_m_from_ms(ms):
    """Convert a duration in milliseconds to a string in h:mm format."""
    duration_minutes = int(ms / (60 * 1000))
    hours = duration_minutes // 60
    minutes = duration_minutes % 60
    return f"{hours}:{minutes:02}"


def report_summary_durations(durations_ms):
    """Print out relevant statistical indicators about this list of durations."""
    # Filter any runs shorter than 5 mins, those were probably aborted early
    durations_ms = [d for d in durations_ms if d >= 5 * 60 * 1000]
    nb_runs = len(durations_ms)

    deciles = quantiles(durations_ms, n=10)
    median = h_m_from_ms(deciles[4])  # 5th decile, but zero-based indexing
    nineth_dec = h_m_from_ms(deciles[8])
    print(f"50% of PR jobs took at most {median} (out of {nb_runs})")
    print(f"10% of PR jobs took at least {nineth_dec} (out of {nb_runs})")


def report_success_rate(nb_good, nb_bad):
    """Print out success rate for a job."""
    nb_runs = nb_good + nb_bad
    success_percent = int(nb_good / nb_runs * 100)
    print(f"Nightly success rate: {success_percent}% (out of {nb_runs})")


def main():
    """Gather and print out all health indicators."""
    try:
        gh_username = os.environ["GITHUB_USERNAME"]
        gh_token = os.environ["GITHUB_API_TOKEN"]
    except KeyError:
        print("You need to provide a github username and API token using")
        print("environment variables GITHUB_USERNAME and GITHUB_API_TOKEN.")
        sys.exit(1)

    since_date = datetime.now() - timedelta(days=7)
    since_timestamp_ms = int(since_date.timestamp()) * 1000

    for name, url in JENKINS_SERVERS.items():
        print(f"\n{name}\n")
        # Note: setting an explicit timeout avoids an incompatibility
        # with some versions of the underlying urllib3, see
        # https://bugs.launchpad.net/python-jenkins/+bug/2018567
        server = jenkins.Jenkins(
            url, username=gh_username, password=gh_token, timeout=60
        )

        nb_good, nb_bad = gather_statuses(server, NIGHTLY_JOB_NAME, since_timestamp_ms)
        report_success_rate(nb_good, nb_bad)

        durations_ms = gather_durations_ms(server, PR_JOB_NAME, since_timestamp_ms)
        report_summary_durations(durations_ms)


main()
