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
    # basic info such as status and dates is available) but makes things
    # slower (about 10x). Only do that for open PRs; we don't need the extra
    # info for old PRs (only the dates which are part of the basic info).
    if p.state == 'open':
        dummy = p.mergeable
    prs.append(p)

# After a branch has been updated, github doesn't immediately go and recompute
# potential conflicts for all open PRs against this branch; instead it does
# that when the info is requested and even then it's done asynchronously: the
# first request might return no data, but if we come back after we've done all
# the other PRs, the info should have become available in the meantime.
for p in prs:
    if p.state == 'open' and p.mergeable is None:
        print(p.number, 'update')
        p.update()

with open("pr-data.p", "wb") as f:
    pickle.dump(prs, f)
