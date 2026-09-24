#!/bin/sh

set -eu

for topic in created closed pending lifetime backlog; do
    echo "PRs $topic..."
    rm -f prs-${topic}.png prs-${topic}.csv
    ./pr-${topic}.py > prs-${topic}.csv
done
