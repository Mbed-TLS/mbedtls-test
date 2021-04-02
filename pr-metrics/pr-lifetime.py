#!/usr/bin/env python3
# coding: utf-8

"""Produce graph of lifetime of PRs over time."""

from prs import pr_dates, quarter, first, last

from collections import defaultdict

import matplotlib.pyplot as plt
from datetime import datetime

first_q = quarter(first)
last_q = quarter(last)

lifetimes_all = defaultdict(list)
lifetimes_com = defaultdict(list)

for beg, end, com in pr_dates():
    # If the PR is still open and it's recent, assign an arbitrary large
    # lifetime. (The exact value doesn't matter for computing the median, as
    # long as it's greater than the median - that is, as long as we've closed
    # at least half the PRs created that quarter. Otherwise the large value
    # will make that pretty visible.)
    if end is None:
        today = datetime.now().date()
        lt_so_far = (today - beg).days
        lt = max(365, lt_so_far)
    else:
        lt = (end - beg).days

    q = quarter(beg)
    lifetimes_all[q].append(lt)
    if com:
        lifetimes_com[q].append(lt)

quarters = tuple(sorted(q for q in lifetimes_all if first_q <= q <= last_q))

for q in quarters:
    lifetimes_all[q].sort()
    lifetimes_com[q].sort()


def median(sl):
    """Return the median value of a sorted list of numbers (0 if empty)."""
    index = (len(sl) - 1) / 2
    if index < 0:
        return 0
    if int(index) == index:
        return sl[int(index)]

    i, j = int(index - 0.5), int(index + 0.5)
    return (sl[i] + sl[j]) / 2


med_all = tuple(median(lifetimes_all[q]) for q in quarters)
med_com = tuple(median(lifetimes_com[q]) for q in quarters)

fig, ax = plt.subplots()
ax.plot(quarters, med_all, "b-", label="median overall")
ax.plot(quarters, med_com, "r-", label="median community")
ax.legend(loc="upper right")
ax.grid(True)
ax.set_xlabel("quarter")
ax.set_ylabel("median lifetime in days of PRs created that quarter")
bot, top = ax.set_ylim()
ax.set_ylim(0, min(365, top))  # we don't care about values over 1 year
fig.suptitle("Median lifetime of PRs per quarter (less is better)")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-lifetime.png")

print("Quarter,median overall,median community")
for q, a, c in zip(quarters, med_all, med_com):
    print("{},{},{}".format(q, int(a), int(c)))
