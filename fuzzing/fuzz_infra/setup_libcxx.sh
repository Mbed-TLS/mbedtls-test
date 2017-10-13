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

# Build and install libc++ with memory sanitizer enabled.

set -eu

git clone --depth 1 -q http://llvm.org/git/libcxx.git
git clone --depth 1 -q http://llvm.org/git/libcxxabi.git

mkdir libcxx/build
mkdir libcxxabi/build && cd libcxxabi/build

cmake -DLIBCXXABI_LIBCXX_PATH=../../libcxx \
    -DLIBCXXABI_LIBCXX_INCLUDES=../../libcxx/include \
    -DLIBCXXABI_ENABLE_SHARED=OFF \
    -DCMAKE_C_COMPILER=clang \
    -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_CXX_FLAGS="-fsanitize=memory -fsanitize-memory-track-origins -fomit-frame-pointer -g -O3" \
    -DCMAKE_INSTALL_PREFIX=/usr/local/libcxx_msan/ \
    ..
make
make install

cd ../../libcxx/build

cmake -DLIBCXX_CXX_ABI=libcxxabi \
    -DLIBCXX_CXX_ABI_INCLUDE_PATHS=../../libcxxabi/include \
    -DLIBCXX_ENABLE_SHARED=OFF \
    -DCMAKE_C_COMPILER=clang \
    -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_CXX_FLAGS="-fsanitize=memory -fsanitize-memory-track-origins -fomit-frame-pointer -g -O3" \
    -DCMAKE_INSTALL_PREFIX=/usr/local/libcxx_msan/ \
    ..

make
make install

cd ../..
rm -rf /fuzzing/libcxx /fuzzing/libcxxabi /fuzzing/build
