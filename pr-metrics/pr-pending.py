#!/usr/bin/python3
# coding: utf-8

"""Produce graph of PRs pending over time."""

from prs import pr_dates

from datetime import date, timedelta
from collections import Counter

import matplotlib.pyplot as plt

cutoff = date(2015, 1, 1)

cnt_tot = Counter()
cnt_com = Counter()

for beg, end, com, cur in pr_dates():
    n_days = (end - beg).days
    dates = Counter(beg + timedelta(days=i) for i in range(n_days))
    cnt_tot.update(dates)
    if com:
        cnt_com.update(dates)

dates = tuple(sorted(d for d in cnt_tot.keys() if d >= cutoff))


def avg(cnt, date):
    """Average number of open PRs over a week."""
    return sum(cnt[date - timedelta(days=i)] for i in range(7)) / 7


nb_tot = tuple(avg(cnt_tot, d) for d in dates)
nb_com = tuple(avg(cnt_com, d) for d in dates)
nb_team = tuple(tot - com for tot, com in zip(nb_tot, nb_com))

fig, ax = plt.subplots()
ax.plot(dates, nb_tot, "b-", label="total")
ax.plot(dates, nb_team, "c-", label="core team")
ax.plot(dates, nb_com, "r-", label="community")
ax.legend(loc="upper left")
ax.grid(True)
ax.set_xlabel("date")
ax.set_ylabel("number of open PRs (sliding average over a week)")
fig.suptitle("Number of PRs pending over time (less is better)")
fig.set_size_inches(12.8, 7.2)  # default 100 dpi -> 720p
fig.savefig("prs-pending.png")

print("date,pending total, pending community")
for d in dates:
    tot, com = cnt_tot[d], cnt_com[d]
    print("{},{},{}".format(d, tot, com))
