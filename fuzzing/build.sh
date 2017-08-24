#!/usr/bin/env sh
#
# Copyright (C) 2017, ARM Limited, All Rights Reserved
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This file is part of mbed TLS (https://tls.mbed.org)

# Builds fuzz_infra and mbedtls fuzz images.

set -eu

print_usage()
{
    echo "Usage: $0 [options]"
    printf "Builds fuzz_infra and mbed TLS fuzz images.\n"
    printf "\n"
    printf "Options\n"
    printf "  -h, --help\tprint this help\n"
    printf "      --tag TAG\ttag the newly created image (mbedtls-fuzzing-tmp by default)\n"
}

TAG=mbedtls-fuzzing-tmp

while [ $# -gt 0 ]
do
    case $1 in
        --tag)
            TAG=$2
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo
            print_usage
            exit 1
            ;;
    esac
    shift
done

if ! docker ps >/dev/null; then
    echo "Error running docker."
    echo
    echo "If on Mac OS, start the Docker service."
    echo "If on Linux, this script needs to be run as root."
    exit 1
fi

docker build fuzz_infra -t fuzz_infra
docker build . -t "${TAG}"
