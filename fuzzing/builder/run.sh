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

# Run build.sh within mbedtls_builder.

set -eu

# TODO arg parsing for --tls-srcs, --fuzz-src and --fuzz-bin

docker run \
    -v "${1:-tls_srcs}":/fuzzing/srcs \
    -v "${2:-fuzz_src}":/fuzzing/src \
    -v "${3:-fuzz_bin}":/fuzzing/bin \
    mbedtls_builder \
    /fuzzing/scripts/build.sh "$@"
