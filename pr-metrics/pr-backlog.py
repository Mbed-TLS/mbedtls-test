#!/usr/bin/env python3
# coding: utf-8

"""Produce analysis of PR backlog over time"""

from prs import pr_dates, first, last, quarter

from datetime import datetime, timedelta
from collections import Counter
from itertools import chain

import matplotlib.pyplot as plt

new_days = 90
old_days = 365

new = Counter()
med = Counter()
old = Counter()

for beg, end, com in pr_dates():
    if end is None:
        tomorrow = datetime.now().date() + timedelta(days=1)
        n_days = (tomorrow - beg).days
    else:
        n_days = (end - beg).days
    for i in range(n_days):
        q = quarter(beg + timedelta(days=i))
        q1 = quarter(beg + timedelta(days=i+1))
        # Only count on each quarter's last day
        if q == q1:
            continue
        if i <= new_days:
            new[q] += 1
        elif i <= old_days:
            med[q] += 1
        else:
            old[q] += 1

first_q = quarter(first)
last_q = quarter(last)

quarters = (q for q in chain(new, med, old) if first_q <= q <= last_q)
quarters = tuple(sorted(set(quarters)))

new_y = tuple(new[q] for q in quarters)
med_y = tuple(med[q] for q in quarters)
old_y = tuple(old[q] for q in quarters)
sum_y = tuple(old[q] + med[q] for q in quarters)

old_name = "older than {} days".format(old_days)
med_name = "medium"
new_name = "recent (less {} days old)".format(new_days)

width = 0.9
fig, ax = plt.subplots()
ax.bar(quarters, old_y, width, label=old_name)
ax.bar(quarters, med_y, width, label=med_name, bottom=old_y)
ax.bar(quarters, new_y, width, label=new_name, bottom=sum_y)
ax.legend(loc="upper left")
ax.grid(True)
ax.set_xlabel("quarter")
ax.set_ylabel("Number or PRs pending")
ax.tick_params(axis="x", labelrotation=90)
fig.suptitle("State of the PR backlog at the end of each quarter")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-backlog.png")

print("Quarter,recent,medium,old,total")
for q in quarters:
    print("{},{},{},{},{}".format(q, new[q], med[q], old[q],
            new[q] + med[q] + old[q]))
