# Dockerfile -- mbed TLS builder
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

# Tools, scripts and settings for building the fuzz targets.

FROM fuzz_infra

ENV CONFIG_FILE_FLAGS -DMBEDTLS_CONFIG_FILE="'\"mbedtls-fuzzing-config.h\"'" -I/fuzzing

ENV CFLAGS -g -O1 -fno-omit-frame-pointer -DFUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION $CONFIG_FILE_FLAGS
ENV ASAN_CFLAGS $CFLAGS -fsanitize-coverage=trace-pc-guard -fsanitize=address,undefined -fno-sanitize-recover=undefined
ENV MSAN_CFLAGS $CFLAGS -fsanitize-coverage=trace-pc-guard -fsanitize=memory -fsanitize-memory-track-origins
ENV FAST_CFLAGS -fsanitize-coverage=trace-pc-guard -O3 -g -DFUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION $CONFIG_FILE_FLAGS
ENV COVERAGE_CFLAGS -fprofile-instr-generate -fcoverage-mapping $CONFIG_FILE_FLAGS

COPY scripts/build.sh /fuzzing/scripts/
COPY scripts/setup_mbedtls.sh /fuzzing/scripts/
COPY scripts/rewrite_symbols.sh /fuzzing/scripts/

COPY mbedtls-fuzzing-config.h /fuzzing/

# my_init acts as init and reaps any zombies
ENTRYPOINT ["/sbin/my_init", "--"]

# run bash (inside init) by default
CMD ["/fuzzing/scripts/build.sh"]
