#!/bin/bash
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

# Runs inside the image, starts fuzzers

set -eu

print_usage()
{
    echo "Usage: $0 [options]"
    printf "Start fuzzer(s)\n"
    printf "\n"
    printf "Options\n"
    printf "  -h, --help\t\t\tprint this help\n"
    printf "  -r, --results RESULTS_DIR\tthe results directory to be used as source for the new corpus\n"
    printf "  -t, --target TARGET\t\tcollect corpus for this fuzz target\n"
    printf "      --mode MODE\twhich fuzzers to start, one of [fast asan msan all afl]\n"
    printf "      --max-len LENGTH\tmax length for generated test cases passed to libFuzzer\n"
}

LOAD_CORPUS=""
while [ $# -gt 0 ]; do
    case "$1" in
        --mode)
            MODE="$2"
            shift
            ;;
        -t|--target)
            TARGET="$2"
            shift
            ;;
        --max-len)
            MAXLEN="-max_len=$2"
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
    shift
done

if [ -z "${TARGET:-}" ]
then
    echo "Provide a target, one of"
    make -f src/Makefile list_targets
    exit 1
fi

mkdir -p "/fuzzing/results/${TARGET}/"
cd "/fuzzing/results/${TARGET}/"

if [ -z "${MODE:-}" ]
then
    echo "Provide a fuzzer mode, one of [afl asan msan fast all]"
    exit 1
fi

corpus_d="/fuzzing/corpora/$TARGET"

mkdir -p afl/queue
mkdir -p libfuzzer/{crashes,queue}
mkdir -p "$corpus_d"
cd libfuzzer/crashes

libfuzzer_args="../queue $corpus_d ../../afl/queue -reload=1 ${MAXLEN:-}"

export UBSAN_OPTIONS=print_stacktrace=1

case "$MODE" in
    afl)
        # FIXME: AFL needs at least one "useful" input in "$corpus_d".
        afl-fuzz -i "$corpus_d" -o afl/ /fuzzing/bin/afl_${TARGET}
        ;;
    msan|fast|asan)
        /fuzzing/bin/fuzz_${MODE}_${TARGET} $libfuzzer_args
        ;;
    all)
        /fuzzing/bin/fuzz_fast_${TARGET} $libfuzzer_args 2>&1 &
        /fuzzing/bin/fuzz_asan_${TARGET} $libfuzzer_args 2>&1 &
        /fuzzing/bin/fuzz_msan_${TARGET} $libfuzzer_args 2>&1 &
        wait
        ;;
    *)
        echo "Invalid mode $MODE; use one of [afl fast asan msan all]"
        exit 1
        ;;
esac
