#!/bin/sh
#
#  Copyright (c) 2017-2022, ARM Limited, All Rights Reserved
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
#   Mount ~/.ssh Also mounts host's ~/.ssh to ~/.ssh
#               in the image. So git can be used.
#
# Usage: ./run.sh mount_dir docker_image_tag
#
#   mount_dir           Directory to mount on the image as the working dir.
#   docker_image_tag    Docker image to run.
#

if [ $# -ne 2 ]
then
    echo "$0: Not all arguments supplied!"
    echo ""
    echo "$0: usage: $0 mount_dir docker_image_tag"
    exit 1
fi
MOUNT_DIR=$1
IMAGE=$2
USR_NAME=`id -un`
USR_ID=`id -u`
USR_GRP=`id -g`
SSH_CFG_PATH=~/.ssh

echo "****************************************************"
echo "  Running docker image - $IMAGE"
echo "  User ID:Group ID --> $USR_ID:$USR_GRP"
echo "  Mounting $SSH_CFG_PATH --> /home/user/.ssh"
echo "  Mounting $MOUNT_DIR --> /var/lib/ws"
echo "****************************************************"

sudo docker run --network=host --rm -i -t -u $USR_ID:$USR_GRP -w /var/lib/ws -v $MOUNT_DIR:/var/lib/ws -v $SSH_CFG_PATH:/home/user/.ssh --cap-add SYS_PTRACE --security-opt seccomp=unconfined ${IMAGE}

