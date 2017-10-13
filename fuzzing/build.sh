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

# Builds fuzz_infra, mbedtls_builder and mbedtls_fuzzer, then runs the builder.

set -eu

print_usage()
{
    echo "Usage: $0 [options]"
    printf "Builds fuzz_infra and mbed TLS fuzz images.\n"
    printf "\n"
    printf "Options\n"
    printf "  -h, --help\tprint this help\n"
    printf "  --fuzzers FUZZ_BINARIES\tan absolute path or a Docker volume where the fuzzing binaries will be installed\n"
    printf "  --tls-sources TLS_SOURCES\tan absolute path or a Docker volume containing sources of Mbed TLS"
    printf "  --fuzz-target-source FUZZ_TARGET_SOURCE\tan absolute path or a Docker volume containing the fuzz target sources"
}

while [ $# -gt 0 ]
do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --fuzzers)
            BUILD_RESULT="$2"
            shift
            ;;
        --tls-sources)
            TLS_SOURCES="$2"
            shift
            ;;
        --fuzz-target-source)
            FUZZ_TARGET_SOURCE="$2"
            shift
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

TLS_SOURCES="${TLS_SOURCES:-$(pwd)/srcs}"
FUZZ_TARGET_SOURCE="${FUZZ_TARGET_SOURCE:-$(pwd)/src}"
BUILD_RESULT="${BUILD_RESULT:-fuzz_bin}"

docker build fuzz_infra -t fuzz_infra
docker build builder -t mbedtls_builder
docker build fuzzer -t mbedtls_fuzzer

./builder/run.sh "$TLS_SOURCES" "$FUZZ_TARGET_SOURCE" "$BUILD_RESULT"
