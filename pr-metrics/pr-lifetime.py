#!/usr/bin/env python3
# coding: utf-8

"""Produce graph of lifetime of PRs over time."""

from prs import pr_dates, quarter, first, last

from collections import defaultdict

import matplotlib.pyplot as plt
from datetime import datetime
from statistics import median
import math

first_q = quarter(first)
last_q = quarter(last)

lifetimes_all_hi = defaultdict(list)
lifetimes_all_lo = defaultdict(list)
lifetimes_com_hi = defaultdict(list)
lifetimes_com_lo = defaultdict(list)

today = datetime.now().date()
for beg, end, com in pr_dates():
    if end is None:
        lo = (today - beg).days
        hi = math.inf
    else:
        hi = lo = (end - beg).days

    q = quarter(beg)
    lifetimes_all_hi[q].append(hi)
    lifetimes_all_lo[q].append(lo)
    if com:
        lifetimes_com_hi[q].append(hi)
        lifetimes_com_lo[q].append(lo)

quarters = tuple(sorted(q for q in lifetimes_all_hi if first_q <= q <= last_q))

med_all_hi = tuple(median(lifetimes_all_hi[q]) for q in quarters)
med_all_lo = tuple(median(lifetimes_all_lo[q]) for q in quarters)
med_com_hi = tuple(median(lifetimes_com_hi[q]) for q in quarters)
med_com_lo = tuple(median(lifetimes_com_lo[q]) for q in quarters)

# skip uncertain quarters in the graph
i = len(quarters)
while med_all_hi[i - 1] != med_all_lo[i - 1] or med_com_hi[i - 1] != med_com_lo[i - 1]:
    i -= 1

fig, ax = plt.subplots()
ax.plot(quarters[:i], med_all_hi[:i], "b-", label="median overall")
ax.plot(quarters[:i], med_com_hi[:i], "r-", label="median community")
ax.legend(loc="upper right")
ax.grid(True)
ax.set_xlabel("quarter")
ax.set_ylabel("median lifetime in days of PRs created that quarter")
ax.tick_params(axis="x", labelrotation=90)
bot, top = ax.set_ylim()
ax.set_ylim(0, min(365, top))  # we don't care about values over 1 year
fig.suptitle("Median lifetime of PRs per quarter (less is better)")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-lifetime.png")


def interval(lo, hi):
    if hi == lo:
        return str(int(hi))
    if math.isinf(hi):
        return "> " + str(int(lo))

    return str(int(lo)) + "-" + str(int(hi))


print("Quarter,median overall,median community")
for i in range(len(quarters)):
    print(
        "{},{},{}".format(
            quarters[i],
            interval(med_all_lo[i], med_all_hi[i]),
            interval(med_com_lo[i], med_com_hi[i]),
        )
    )
