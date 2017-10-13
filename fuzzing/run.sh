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

# Run an mbed TLS fuzz image and run a command.

set -eu

print_usage()
{
    echo "Usage: $0 [option]... command"
    printf "Run an mbed TLS fuzz image and run a command.\n"
    printf "\n"
    printf "Options\n"
    printf "  -h, --help\tprint this help\n"
    printf "  -r, --results RESULTS_DIR\tan absolute path or a Docker volume where results are stored\n"
    printf "  --fuzzers FUZZ_BINARIES\tan absolute path or a Docker volume where the fuzzing binaries are installed\n"
    printf "  --corpus FUZZ_CORPUS\tan absolute path or a Docker volume containing fuzz corpora\n"
}


while [ $# -gt 0 ]
do
    case "$1" in
        -r|--results)
            RESULT_DIR="$2"
            shift
            ;;
        --fuzzers)
            FUZZ_BINARIES="$2"
            shift
            ;;
        --corpus)
            FUZZ_CORPUS="$2"
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            break
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

FUZZ_BINARIES="${FUZZ_BINARIES:-fuzz_bin}"
RESULT_DIR="${RESULT_DIR:-$(pwd)/results}"
FUZZ_CORPUS="${FUZZ_CORPUS:-$(pwd)/corpora}"

docker run \
    --log-driver none \
    -v "${RESULT_DIR}:/fuzzing/results" \
    -v "${FUZZ_BINARIES}:/fuzzing/bin" \
    -v "${FUZZ_CORPUS}:/fuzzing/corpora" \
    -it \
    mbedtls_fuzzer \
    "$@"

