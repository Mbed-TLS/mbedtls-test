#!/bin/sh
#
# List the docker image tag used by Jenkins, for each specified Dockerfile or
# for the Dockerfile in each subdirectory of this folder. See
# gen_dockerfile_builder_job in vars/gen_jobs.groovy.

set -e

arch=$(uname -m)
case $arch in
    aarch64) arch=arm64;;
    x86_64) arch=amd64;;
esac

list_one_dockerfile () {
    dir="${1%/Dockerfile}"
    dir="${dir%/}"
    hash="$(git hash-object "$dir/Dockerfile")"
    base="$(basename -- "$dir")"
    if [ "$base" = "arm-compilers" ] && [ "$arch" != "amd64" ]; then
        continue
    fi
    echo "$base-$hash-$arch"
}

if [ $# -eq 0 ]; then
    set -- "$(dirname -- "$0")"/*/Dockerfile
fi

for d in "$@"; do
    list_one_dockerfile "$d"
done
