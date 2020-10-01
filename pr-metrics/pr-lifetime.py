#!/usr/bin/env python3
# coding: utf-8

"""Produce graph of lifetime of PRs over time."""

from prs import pr_dates, quarter

from collections import defaultdict
from numpy import percentile

import matplotlib.pyplot as plt

cutoff = "15q1"

lifetimes_all = defaultdict(list)
lifetimes_com = defaultdict(list)

for beg, end, com, cur in pr_dates():
    lt = (end - beg).days
    q = quarter(beg)
    lifetimes_all[q].append(lt)
    if com:
        lifetimes_com[q].append(lt)

quarters = tuple(sorted(q for q in lifetimes_all if q >= cutoff))

for q in quarters:
    lifetimes_all[q].sort()
    lifetimes_com[q].sort()

c50_all = tuple(percentile(lifetimes_all[q], 50) for q in quarters)
c50_com = tuple(percentile(lifetimes_com[q], 50) for q in quarters)

c75_all = tuple(percentile(lifetimes_all[q], 75) for q in quarters)
c75_com = tuple(percentile(lifetimes_com[q], 75) for q in quarters)

c90_all = tuple(percentile(lifetimes_all[q], 90) for q in quarters)
c90_com = tuple(percentile(lifetimes_com[q], 90) for q in quarters)

fig, ax = plt.subplots()
ax.plot(quarters, c50_all, "b-", label="median overall")
ax.plot(quarters, c50_com, "r-", label="median community")
#ax.plot(quarters, c75_all, "b--", label="3rd quartile overall")
#ax.plot(quarters, c75_com, "r--", label="3rd quartile community")
ax.plot(quarters, c90_all, "b:", label="90th centile overall")
ax.plot(quarters, c90_com, "r:", label="90th centile community")
ax.legend(loc="upper right")
ax.grid(True)
ax.set_xlabel("quarter")
ax.set_ylabel("median/c75/c90 lifetime in days of PRs created that quarter")
bot, top = ax.set_ylim()
ax.set_ylim(0, min(365, top))  # we don't care about values over 1 year
fig.suptitle("Median/c75/c90 lifetime of PRs per quarter (less is better)")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-lifetime.png")

print("Quarter,median overall,median community")
for q, a, c in zip(quarters, c50_all, c50_com):
    print("{},{},{}".format(q, int(a), int(c)))
