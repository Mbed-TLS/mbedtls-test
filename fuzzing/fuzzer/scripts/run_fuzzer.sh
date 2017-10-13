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

    # FIXME: src/ is unlikely to be available when running the fuzzer. Could be
    # fixed by cashing the output of `make -f src/Makefile list_targets` in the
    # Docker image, or by looking at the contents of bin/

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

[ -f /tmp/pid ] && exit 666

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
        # To whoever it may concern, I apologise for the next two lines of code.
        #
        # filter_libfuzzer_output.py filters the output of fuzz_* to reduce the
        # output visible to buildbot to avoid overflowing log files to the point
        # where the browser crashes when viewing said log file.
        #
        # As its second function, it serves as a timeout on fuzz_*. If no
        # progress is made for an hour, or a full day has passed since the
        # script started, filter_libfuzzer_output.py will exit with a non-zero
        # exit code.
        #
        # However, to ensure that buildbot does not see such a time out as a
        # failure, the return code of the next line needs to be zero in that
        # scenario. And, when there is an error (or an exit() due to a crash
        # found) from fuzz_*, we want *that* exit code to be reported to
        # buildbot. Bash's pipefail ensures that we get the right-most non-zero
        # exit code.
        #
        # Unfortunately, there appears to be a bug that prevents fuzz_* to quit
        # on SIGPIPE. Initially, this was thought to be a libFuzzer bug
        # (https://bugs.llvm.org/show_bug.cgi?id=34999) but it does not
        # reproduce without mbed TLS. The end result is that:
        #
        # 1. On the left hand of the pipe, fuzz_* is started in the background,
        #    its pid is recorded in /tmp/pid (which is unique to the Docker
        #    container) and this subshell waits for fuzz_* to exit.
        # 2. The right hand of the pipe runs the filter, which, if it times out
        #    (with non-zero exit code), results in killing the fuzz_* process
        #    using the pid stored in /tmp/pid (which causes wait to exit as
        #    well).
        #
        # Finally, the case of `|| true` after `kill`. There is a race condition
        # here.
        #
        # If fuzz_* (and hence wait) exits at the same time as
        # filter_libfuzzer_output.py exits with a non-zero exit code, then kill
        # will fail and return a non-zero exit code. With pipefail, as I
        # understand, the return code for the whole shell command would be the
        # first (reading from right-to-left) non-zero exit code, which is the
        # return code form kill in this scenario. However, we want buildbot to
        # see the return code from fuzz_* (which for whatever reason, might even
        # be zero).  With || true, the right-hand subshell always returns zero,
        # and we'll get the return code from wait, which is the return code from
        # fuzz_*.
        #
        # FIXME: If the bug relating to fuzz_* not exiting on SIGPIPE ever gets
        # fixed, this can be simplified to:
        #
        # set -o pipefail; /fuzzing/bin/fuzz_${MODE}_${TARGET} $libfuzzer_args 2>&1 | /fuzzing/scripts/filter_libfuzzer_output.py
        #
        # and filter_libfuzzer_output.py should be changed to return 0 on
        # time-out.
        #
        # FIXME: Finally, it would be nice to have the option of running fuzz_*
        # wihtout the filter in case we are not running in buildbot. This might
        # even be a saner default.
        set -o pipefail
        ( /fuzzing/bin/fuzz_${MODE}_${TARGET} $libfuzzer_args 2>&1 & echo $! > /tmp/pid; wait $( cat /tmp/pid ) ) | ( /fuzzing/scripts/filter_libfuzzer_output.py || ( kill $( cat /tmp/pid ) || true ) )
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
