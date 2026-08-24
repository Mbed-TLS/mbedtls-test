#!/bin/sh

# Build the specified Dockerfile(s).
# Follow the image naming convention used on Jenkins, which uses a hash
# of the Dockerfile contents.

set -e

usage () {
    cat <<EOF
Usage: $0 [OPTION]... DIR[/Dockerfile]...
Build the specified Docker images.

  -s PREFIX     Prefix for the docker command (default: sudo)
EOF
}

if [ "$1" = "--help" ]; then
    usage
    exit
fi

SUDO=sudo

while getopts s: OPTLET; do
    case $OPTLET in
        s) SUDO=$OPTARG;;
        \?) usage >&2; exit 1;;
    esac
done
shift $((OPTIND - 1))

list_sh="$(dirname -- "$0")/list-docker-image-tags.sh"

build () {
    if [ -d "$1" ]; then
        set -- "$1/Dockerfile"
    fi
    tag="$("$list_sh" "$1")"
    $SUDO docker build --network=host -t "$tag" -f "$1" "${1%/*}"
    echo "Built $tag"
}

for d in "$@"; do
    build "$d"
done
