#!/usr/bin/python3
# coding: utf-8

"""Produce summary or PRs pending per branch and their mergeability status."""

import pickle
from datetime import datetime
from collections import Counter

with open("pr-data.p", "rb") as f:
    prs = pickle.load(f)

c_open = Counter()
c_mergeable = Counter()
c_nowork = Counter()
c_recent = Counter()

for p in prs:
    if p.state != "open":
        continue

    branch = p.base.ref
    c_open[branch] += 1
    if p.mergeable:
        c_mergeable[branch] += 1
        if "needs: work" not in [l.name for l in p.labels]:
            c_nowork[branch] += 1
            days = (datetime.now() - p.updated_at).days
            if days < 31:
                c_recent[branch] += 1


print("branch: open, no conflicts, minus need work, minus month-old")
for b in sorted(c_open, key=lambda b: c_open[b], reverse=True):
    print(
        "{:>15}: {: 4}, {: 3}, {: 3}, {: 3}".format(
            b, c_open[b], c_mergeable[b], c_nowork[b], c_recent[b]
        )
    )
