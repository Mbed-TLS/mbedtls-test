#!/bin/sh
#
#  Copyright (c) 2017-2021, ARM Limited, All Rights Reserved
#  SPDX-License-Identifier: Apache-2.0
#
#  Licensed under the Apache License, Version 2.0 (the "License"); you may
#  not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
#
# Purpose
#
# This is a helper script to start a docker container with common features.
# 
# Features:
#   User Ids    User/Grp Ids are specified same as the host user so that files
#               created/updated by docker image can be accessible after
#               exiting the image.
#   Mount dir   Mounts a user specified dir to the working dir in the image.

usage () {
    cat <<EOF
Usage: $0 [OPTION]... MOUNT_DIR DOCKER_IMAGE_TAG
Run an Mbed TLS CI Docker image.

Example:
    $0 . \$(${0%/*}/list-docker-image-tags.sh ${0%/*}/ubuntu-16.04)

MOUNT_DIR           Directory to mount on the image as the working dir.
DOCKER_IMAGE_TAG    Docker image to run.

  -o DIR        Directory to mount at /opt/host (default: /opt/host)
EOF
}

if [ "$1" = "--help" ]; then
    usage
    exit
fi

OPT_HOST=/opt/host

while getopts o: OPTLET; do
    case $OPTLET in
        o) OPT_HOST=$OPTARG;;
        \?) usage >&2; exit 1;;
    esac
done
shift $((OPTIND - 1))

if [ $# -le 1 ]; then
    echo "$0: Not enough arguments (need MOUNT_DIR DOCKER_IMAGE_TAG)"
    usage >&2
    exit 1
fi

if [ $# -gt 2 ]; then
    echo >&2 "$0: Too many arguments"
    exit 1
fi

MOUNT_DIR=$1
IMAGE=$2

USR_NAME=`id -un`
USR_ID=`id -u`
USR_GRP=`id -g`

set --
if [ -d "$OPT_HOST" ]; then
    set -- -v "$OPT_HOST":/opt/host:ro
else
    OPT_HOST=
fi

echo "****************************************************"
echo "  Running docker image - $IMAGE"
echo "  User ID:Group ID --> $USR_ID:$USR_GRP"
echo "  Mounting $MOUNT_DIR --> /var/lib/ws"
if [ -n "$OPT_HOST" ]; then
    echo "  Mounting $OPT_HOST --> /opt/host"
fi
echo "****************************************************"

sudo docker run --network=host --rm -i -t -u $USR_ID:$USR_GRP -w /var/lib/ws -e HOME=/var/lib/ws -v $MOUNT_DIR:/var/lib/ws "$@" --cap-add SYS_PTRACE ${IMAGE}

