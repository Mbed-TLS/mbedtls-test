These scripts collect some metrics about mbed TLS PRs over time.

Usage:

1. `./get-pr-data.py` - this takes a long time and requires the environment
   variable `GITHUB_API_TOKEN` to be set to a valid [github API
token](https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) (unauthenticated access to the API has a limit on the number or requests that is too low for our number of PRs). It generates `pr-data.p` with pickled data.
2. `./do.sh` - this works offline from the data in `pr-data.p` and generates a
   bunch of png and csv files.

These scripts work with matplotlib 3.1.2 (python 3.8.2, ubuntu 20.04), but
appear to be broken with matplotlib 1.5.1 (python 3.5, ubuntu 16.04).

They require pygithub, which can easily be installed with pip (any version
should do).
