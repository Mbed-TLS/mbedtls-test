# Dockerfile -- mbed TLS fuzzing
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

# Includes mbed TLS from ./mbedtls_src and builds it for all fuzzers. Adds all
# fuzz targets and builds fuzz and standalone executables for all variants.

FROM fuzz_infra

WORKDIR /fuzzing

ENV CFLAGS -g -O1 -fno-omit-frame-pointer -DFUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
ENV ASAN_CFLAGS $CFLAGS -fsanitize-coverage=trace-pc-guard -fsanitize=address,undefined -fno-sanitize-recover=undefined
ENV MSAN_CFLAGS $CFLAGS -fsanitize-coverage=trace-pc-guard -fsanitize=memory -fsanitize-memory-track-origins
ENV FAST_CFLAGS -fsanitize-coverage=trace-pc-guard -O3 -g -DFUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION

# Build all mbed TLS variants
COPY scripts/setup_mbedtls.sh /fuzzing/

COPY srcs/ /fuzzing/srcs

RUN /fuzzing/setup_mbedtls.sh /fuzzing/srcs/mbedtls_a "a"
RUN /fuzzing/setup_mbedtls.sh /fuzzing/srcs/mbedtls_b "b"

COPY scripts/rewrite_symbols.sh /fuzzing/scripts/
RUN /fuzzing/scripts/rewrite_symbols.sh /usr/local/mbedtls-*

# Include fuzz target sources
COPY src /fuzzing/src

# Build and "install" fuzz targets
WORKDIR /fuzzing/src
RUN ./configure && make && make install && make clean

ENV MSAN_SYMBOLIZER_PATH=/usr/bin/llvm-symbolizer

# Load corpora into image
#
# NOTE: if the collection of corpora becomes large, it might start having an
# impact on image size. Currently, image size is dominated by compiler
# installations.
RUN mkdir /fuzzing/corpora
COPY corpora/ /fuzzing/corpora/
COPY scripts/ /fuzzing/scripts/
COPY root_ca.crt /fuzzing/

WORKDIR /fuzzing

# my_init acts as init and reaps any zombies
ENTRYPOINT ["/sbin/my_init", "--"]

# run bash (inside init) by default
CMD ["bash"]
