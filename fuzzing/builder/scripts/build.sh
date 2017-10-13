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

# Builds mbed TLS fuzz targets.  Expects Mbed TLS sources in /fuzzing/srcs/,
# and fuzz target sources in /fuzzing/src/; produces statically linked
# executables in /fuzzing/bin/.

set -eu

cd /fuzzing/scripts/

# TODO To allow any TLS implementation to be included in the fuzzer, generalise
# this to "for d in /fuzzing/srcs/*; do; ./setup.sh $d; done". ./setup.sh
# should know how to build each library and give it a proper prefix.

./setup_mbedtls.sh --version "a" /fuzzing/srcs/mbedtls_a
./setup_mbedtls.sh --version "b" /fuzzing/srcs/mbedtls_b

./rewrite_symbols.sh /usr/local/a/mbedtls-*

cd /fuzzing/src/

# Installs into /fuzzing/bin
./configure && make && make install && make clean
