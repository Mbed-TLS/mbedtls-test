#!/bin/sh
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

# Collect and merge results into a new corpus into
# /fuzzing/results/fuzz_target/new_corpus

set -eu

while [ $# -gt 1 ]; do
    case $1 in
        -t|--target)
            TARGET="$2"
            shift
            ;;
    esac
    shift
done

if [ -z "${TARGET:-}" ]
then
    echo "Must specify target, use -t or --target"
    exit 1
fi

case $TARGET in
    *\ *)
        echo "Target must not contain spaces: $TARGET"
        exit 1
esac

DIRS="/fuzzing/corpora/$TARGET /fuzzing/results/$TARGET/in /fuzzing/results/$TARGET/queue/out"
TEST_DIRS=""
for DIR in $DIRS; do
    [ -d $DIR ] && TEST_DIRS="$TEST_DIRS $DIR"
done

if [ -z "$TEST_DIRS" ]; then
    echo "No results found."
    exit 0
fi

mkdir -p "/fuzzing/results/$TARGET/new_corpus"

/fuzzing/bin/fuzz_fast_$TARGET -merge=1 \
    /fuzzing/results/$TARGET/new_corpus \
    $TEST_DIRS
