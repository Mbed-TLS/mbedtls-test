#!/bin/sh
#
# Copyright (c) 2017, ARM Limited, All Rights Reserved
#
# Purpose
#
# This is a helper script to starts a docker image with a common work config.
# 
# Config:
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
    echo "$0: No arguments supplied!"
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

sudo docker run --rm -i -u $USR_ID:$USR_GRP -w /var/lib/ws -v $MOUNT_DIR:/var/lib/ws -v $SSH_CFG_PATH:/home/user/.ssh ${IMAGE}
