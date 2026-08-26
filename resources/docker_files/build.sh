#!/bin/sh

# Build the specified Dockerfile(s).
# Follow the image naming convention used on Jenkins, which uses a hash
# of the Dockerfile contents.

set -e

usage () {
    cat <<EOF
Usage: $0 [OPTION]... DIR[/Dockerfile]...
Build the specified Docker images.

  -C FILE       File to use as armc6_url (for building arm-compilers)
  -H SHA        SHA-256 hash of armc6_url (for building arm-compilers)
  -s PREFIX     Prefix for the docker command (default: sudo)
EOF
}

if [ "$1" = "--help" ]; then
    usage
    exit
fi

ARM_COMPILER_FILE=
ARM_COMPILER_TEMP=
ARM_COMPILER_HASH=
SUDO=sudo

while getopts C:H:s: OPTLET; do
    case $OPTLET in
        C) ARM_COMPILER_FILE=$OPTARG;;
        H) ARM_COMPILER_HASH=$OPTARG;;
        s) SUDO=$OPTARG;;
        \?) usage >&2; exit 1;;
    esac
done
shift $((OPTIND - 1))

list_sh="$(dirname -- "$0")/list-docker-image-tags.sh"

build () {
    if [ -d "$1" ]; then
        dir="$1"
        dockerfile="$dir/Dockerfile"
    else
        dir="$(dirname -- "$1")"
        dockerfile="$1"
    fi
    set --
    tag="$("$list_sh" "$dir")"

    if [ -n "$ARM_COMPILER_FILE" ]; then
        ARM_COMPILER_TEMP="$dir/arm_compiler.$$.tmp"
        arm_compiler_url="file:///run/context/arm_compiler.$$.tmp"
        cp -p -f -- "$ARM_COMPILER_FILE" "$ARM_COMPILER_TEMP"
        set -- "$@" --secret type=env,id=armc6_url,env=ARM_COMPILER_URL
    fi
    if [ -n "$ARM_COMPILER_HASH" ]; then
        set -- "$@" --build-arg ARMC6_SHA256="$ARM_COMPILER_HASH"
    fi

    $SUDO ARM_COMPILER_URL="$arm_compiler_url" docker build --network=host -t "$tag" -f "$dockerfile" "$@" "$dir"
    rm -f "$ARM_COMPILER_TEMP"
    ARM_COMPILER_TEMP=
    echo "Built $tag"
}

for d in "$@"; do
    build "$d"
done
