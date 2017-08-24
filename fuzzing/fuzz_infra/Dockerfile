# Dockerfile -- fuzz_infra
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

# Docker image which installs the required fuzzing tools, including builds of
# libc++ and libFuzzer.


# Use phusion/baseimage as the base image. It is a minimal Ubuntu with
# modifications to make it more Docker friendly, including a custom init
# system. It makes it feasible to run multiple processes (as the fuzzers
# sometimes do) inside the Docker image while guaranteeing proper clean-up. See
# https://github.com/phusion/baseimage-docker#docker_single_process for more
# information.
FROM phusion/baseimage

RUN echo "deb http://apt.llvm.org/xenial/ llvm-toolchain-xenial main" > /etc/apt/sources.list.d/clang.list
RUN curl https://apt.llvm.org/llvm-snapshot.gpg.key|apt-key add -

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update -q && apt-get install -y --no-install-recommends \
    afl-clang \
    clang>=6.0 \
    cmake \
    dpkg-dev \
    git \
    llvm \
    llvm-dev \
    make \
    && apt-get clean \
    && rm -rf /tmp/* /var/tmp/* /var/lib/apt/lists/*

WORKDIR /fuzzing
COPY setup_libcxx.sh /fuzzing
RUN ./setup_libcxx.sh

WORKDIR /fuzzing
COPY setup_libfuzzer.sh /fuzzing
RUN ./setup_libfuzzer.sh

RUN rm setup_libcxx.sh setup_libfuzzer.sh
