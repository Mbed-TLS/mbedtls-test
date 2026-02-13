#!/usr/bin/env python3
# coding: utf-8

"""Produce summary or PRs pending per branch and their mergeability status."""

import pickle
from datetime import datetime
from collections import Counter

with open("pr-data.p", "rb") as f:
    prs = pickle.load(f)

c_open = Counter()
c_mergeable = Counter()
c_recent = Counter()
c_recent2 = Counter()

for p in prs:
    if p.state != "open":
        continue

    branch = p.base.ref
    c_open[branch] += 1
    if p.mergeable:
        c_mergeable[branch] += 1
        days = (datetime.now() - p.updated_at).days
        if days < 31:
            c_recent[branch] += 1
        if days < 8:
            c_recent2[branch] += 1


print("              branch:       open,  mergeable,       <31d,        <8d")
for b in sorted(c_open, key=lambda b: c_open[b], reverse=True):
    print("{:>20}: {: 10}, {: 10}, {: 10}, {:10}".format(
            b, c_open[b], c_mergeable[b], c_recent[b], c_recent2[b]))
