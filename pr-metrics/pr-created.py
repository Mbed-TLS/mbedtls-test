#!/usr/bin/env python3
# coding: utf-8

"""Produce graph of PRs created by time period."""

from prs import pr_dates, quarter

from collections import Counter

import matplotlib.pyplot as plt

cutoff = "15q1"

cnt_all = Counter()
cnt_com = Counter()

for beg, end, com, cur in pr_dates():
    q = quarter(beg)
    cnt_all[q] += 1
    if com:
        cnt_com[q] += 1

quarters = tuple(sorted(q for q in cnt_all if q >= cutoff))

prs_com = tuple(cnt_com[q] for q in quarters)
prs_team = tuple(cnt_all[q] - cnt_com[q] for q in quarters)

width = 0.9
fig, ax = plt.subplots()
ax.bar(quarters, prs_com, width, label="community")
ax.bar(quarters, prs_team, width, label="core team", bottom=prs_com)
ax.legend(loc="upper left")
ax.grid(True)
ax.set_xlabel("quarter")
ax.set_ylabel("Number or PRs created")
fig.suptitle("Number of PRs created per quarter")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-created.png")

print("Quarter,community created,total created")
for q in quarters:
    print("{},{},{}".format(q, cnt_com[q], cnt_all[q]))
