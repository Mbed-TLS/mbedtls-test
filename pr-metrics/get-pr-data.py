#!/usr/bin/env python3
# coding: utf-8

"""Get PR data from github and pickle it."""

import pickle
import os

from github import Github

if "GITHUB_API_TOKEN" in os.environ:
    token = os.environ["GITHUB_API_TOKEN"]
else:
    print("You need to provide a GitHub API token")

g = Github(token)
r = g.get_repo("ARMMbed/mbedtls")

prs = list()
for p in r.get_pulls(state="all"):
    print(p.number)
    # Accessing p.mergeable forces completion of PR data (by default, only
    # basic info such as status and dates is available) but makes the script
    # slower (about 10x).
    # Leave commented as we only need the basic info for do.sh.
    # (Uncomment if you want to use extended PR data with other scripts.)
    #dummy = p.mergeable
    prs.append(p)

with open("pr-data.p", "wb") as f:
    pickle.dump(prs, f)
