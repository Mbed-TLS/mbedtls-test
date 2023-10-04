#!/usr/bin/env python3
# coding: utf-8

"""Produce analysis of PR backlog over time"""

from prs import pr_dates, first, last, quarter

from datetime import datetime, timedelta
from collections import Counter
from itertools import chain

import matplotlib.pyplot as plt

# Group PRs by age, according to these thresholds
thresholds = [15, 90, 180, 365, 365 * 1000]

counters   = {t: Counter() for t in thresholds}

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
        for t in thresholds:
            if i <= t:
                counters[t][q] += 1
                break

first_q = quarter(first)
last_q = quarter(last)

quarters = (q for q in chain(*counters.values()) if first_q <= q <= last_q)
quarters = tuple(sorted(set(quarters)))

buckets_y = {t: tuple(counters[t][q] for q in quarters) for t in thresholds}

names = {t: None} #f"<= {t} days" for t in thresholds}
names[thresholds[0]] = f"<= {thresholds[0]} days"
for i in range(1, len(thresholds)):
    names[thresholds[i]] = f"{thresholds[i-1] + 1}..{thresholds[i]} days"
names[thresholds[-1]] = f"> {thresholds[-2]} days"

width = 0.9
fig, ax = plt.subplots()
prev_tops=[0] * len(quarters)
for t in reversed(thresholds):
    ax.bar(quarters, buckets_y[t], width, label=names[t], bottom=prev_tops)
    prev_tops = [a + b for a,b in zip(prev_tops, buckets_y[t])]
ax.legend(loc="upper left")
ax.grid(True)
#ax.set_xlabel("quarter")
ax.set_ylabel("Open PR count")
ax.tick_params(axis="x", labelrotation=90)
fig.suptitle("Open PR count, broken down by PR age")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-backlog.png")

print("Quarter,recent,medium,old,total")
for q in quarters:
    s = ", ".join(str(counters[t][q]) for t in thresholds)
    t = sum(counters[t][q] for t in thresholds)
    print(f"{q}, {s}, {t}")
