#!/usr/bin/env python3
# coding: utf-8

"""PR data an misc common functions."""

import pickle
import datetime
import os

with open("pr-data.p", "rb") as f:
    prs = pickle.load(f)


_team_logins = (
    "gilles-peskine-arm",
    "hanno-arm",
    "RonEld",
    "andresag01",
    "mpg",
    "sbutcher-arm",
    "Patater",
    "k-stachowiak",
    "AndrzejKurek",
    "yanesca",
    "mazimkhan",
    "dgreen-arm",
    "artokin",
    "jarlamsa",
    "piotr-now",
    "pjbakker",
    "jarvte",
    "danh-arm",
    "ronald-cron-arm",
    "paul-elliott-arm",
    "gabor-mezei-arm",
    "bensze01",
)


def is_community(pr):
    """Return False if the PR is from a team member or from inside Arm."""
    labels = tuple(l.name for l in pr.labels)
    # starting from 2021 we consistently label community PRs
    if pr.created_at.date().year >= 2021:
        return "Community" in labels

    # before that we used to inconsistently labeled PRs from the team or ARM,
    # so complement that with a list of team members
    if "mbed TLS team" in labels or "Arm Contribution" in labels:
        return False
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
