#!/usr/bin/env python3
# coding: utf-8

"""PR data an misc common functions."""

import pickle
import datetime
import os

with open("pr-data.p", "rb") as f:
    prs = pickle.load(f)


# current and past team members, alphabetical order (sort -f)
_team_logins = (
    "aditya-deshpande-arm",
    "andresag01",
    "AndrzejKurek",
    "artokin",
    "bensze01",
    "brett-warren-arm",
    "d3zd3z",
    "danh-arm",
    "daverodgman",
    "davidhorstmann-arm",
    "dgreen-arm",
    "gabor-mezei-arm",
    "gilles-peskine-arm",
    "hanno-arm",
    "jackbondpreston-arm",
    "jarlamsa",
    "jarvte",
    "JoeSubbiani",
    "k-stachowiak",
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
    "tom-cosgrove-arm",
    "tom-daubney-arm",
    "tuvshinzayaArm",
    "wernerlewis",
    "xkqian",
    "yanesca",
    "yuhaoth",
    "yutotakano",
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


first = datetime.date(2015, 1, 1)
last = datetime.date(2099, 12, 31)
if "PR_LAST_DATE" in os.environ:
    last_str = os.environ["PR_LAST_DATE"]
    last = datetime.datetime.strptime(last_str, "%Y-%m-%d").date()
if "PR_FIRST_DATE" in os.environ:
    first_str = os.environ["PR_FIRST_DATE"]
    first = datetime.datetime.strptime(first_str, "%Y-%m-%d").date()
