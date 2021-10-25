#!/bin/sh

# Build the specified Dockerfile(s).
# Follow the image naming convention used on Jenkins, which uses a hash
# of the Dockerfile contents.

set -e

if [ $# -eq 0 ] || [ "$1" = "--help" ]; then
    cat <<EOF
Usage: $0 DIR/Dockerfile[...]
Build the specified Docker images.
EOF
    exit
fi

list_sh="$(dirname -- "$0")/list-docker-image-tags.sh"

build () {
    if [ -d "$1" ]; then
        set -- "$1/Dockerfile"
    fi
    tag="$("$list_sh" "$1")"
    sudo docker build --network=host -t "$tag" -f "$1" "${1%/*}"
}

for d in "$@"; do
    build "$d"
done
