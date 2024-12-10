#!/bin/sh
#
# List the docker image tag used by Jenkins, for each specified Dockerfile or
# for the Dockerfile in each subdirectory of this folder. See
# gen_dockerfile_builder_job in vars/gen_jobs.groovy.

set -e

list () {
    dir="${1%/Dockerfile}"
    dir="${dir%/}"
    hash="$(git hash-object "$dir/Dockerfile")"
    base="$(basename -- "$dir")"
    echo "$base-$hash-amd64"
    if [ "$base" != "arm-compilers" ]; then
        echo "$base-$hash-arm64"
    fi
}

if [ $# -eq 0 ]; then
    set -- "$(dirname -- "$0")"/*/Dockerfile
fi

for d in "$@"; do
    list "$d"
done
