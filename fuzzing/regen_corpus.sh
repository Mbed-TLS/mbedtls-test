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

# Collect and merge results into a new corpus in ./corpora/fuzz_target

set -eu

print_usage()
{
    echo "Usage: $0 [options]"
    printf "Collect and merge results into a new corpus in ./corpora/TARGET\n"
    printf "\n"
    printf "Options\n"
    printf "  -h, --help\t\t\tprint this help\n"
    printf "  -r, --results RESULTS_DIR\tthe results directory to be used as source for the new corpus\n"
    printf "  -t, --target TARGET\t\tcollect corpus for this fuzz target\n"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -t|--target)
            TARGET="$2"
            shift
            ;;
        -r|--results)
            RESULTS="$2"
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

RESULTS="${RESULTS:-/scratch/mbedtls/results}"

if [ -z "${TARGET:-}" ]
then
    echo "Must specify target, use -t or --target"
    exit 1
fi

mkdir -p "${RESULTS}/${TARGET}/new_corpus"
./run.sh \
    --results "${RESULTS}" \
    ./scripts/regen_corpus.sh --target "$TARGET"

rm -rf "corpora/${TARGET}"
mv "${RESULTS}/${TARGET}/new_corpus" "corpora/${TARGET}"
