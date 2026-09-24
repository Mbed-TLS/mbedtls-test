#!/usr/bin/env python3
# coding: utf-8

"""PR data an misc common functions."""

import pickle
import datetime
import os

with open("pr-data.p", "rb") as f:
    prs = pickle.load(f)


# Current and past core contributors, alphabetical order (sort -f).
#
# That is, people who are or have been in one of:
# - https://github.com/orgs/Mbed-TLS/teams/mbed-tls-reviewers/members
# - https://github.com/orgs/Mbed-TLS/teams/mbed-tls-developers/members
# The list is maintained manually in order to retain past members.
_team_logins = (
    "adeaarm",
    "aditya-deshpande-arm",
    "andresag01",
    "AndrzejKurek",
    "artokin",
    "bensze01",
    "brett-warren-arm",
    "chris-jones-arm",
    "d3zd3z",
    "danh-arm",
    "daverodgman",
    "davidhorstmann-arm",
    "dgreen-arm",
    "gabor-mezei-arm",
    "gilles-peskine-arm",
    "hanno-arm",
    "hanno-becker",
    "jackbondpreston-arm",
    "jarlamsa",
    "jarvte",
    "JoeSubbiani",
    "k-stachowiak",
    "laumor01",
    "lpy4105",
    "lukgni",
    "mazimkhan",
    "minosgalanakis",
    "mpg",
    "mprse",
    "mstarzyk-mobica",
    "Patater",
    "paul-elliott-arm",
    "piotr-now",
    "pjbakker",
    "RcColes",
    "ronald-cron-arm",
    "RonEld",
    "sbutcher-arm",
    "shanechko",
    "silabs-hannes",
    "silabs-Kusumit",
    "silabs-Saketh",
    "superna9999",
    "tom-cosgrove-arm",
    "tom-daubney-arm",
    "tuvshinzayaArm",
    "valeriosetti",
    "wernerlewis",
    "xkqian",
    "yanesca",
    "yanrayw",
    "yuhaoth",
    "yutotakano",
    "Zaya-dyno",
    "zhangsenWang",
)


def is_community(pr):
    """Return False if the PR is from a team member."""
    if pr.user.login in _team_logins:
        return False
    return True


def quarter(date):
    """Return a string decribing this date's quarter, for example 19q3."""
    q = str(date.year % 100)
    q += "q"
    q += str((date.month + 2) // 3)
    return q


def pr_dates():
    """Iterate over PRs with open/close dates and community status."""
    for pr in prs:
        beg = pr.created_at.date()
        end = pr.closed_at.date() if pr.closed_at else None
        com = is_community(pr)
        yield (beg, end, com)


# default start date: 2020-01-01 (when we moved to tf.org)
first = datetime.date(2020, 1, 1)
# default end date: end of the previous quarter
last = datetime.datetime.now().date()
current_q = quarter(last)
while quarter(last) == current_q:
    last -= datetime.timedelta(days=1)
# default start/end dates can be overriden from the environment
if "PR_LAST_DATE" in os.environ:
    last_str = os.environ["PR_LAST_DATE"]
    last = datetime.datetime.strptime(last_str, "%Y-%m-%d").date()
if "PR_FIRST_DATE" in os.environ:
    first_str = os.environ["PR_FIRST_DATE"]
    first = datetime.datetime.strptime(first_str, "%Y-%m-%d").date()
