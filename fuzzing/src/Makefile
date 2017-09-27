# Makefile -- fuzz targets
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

# This file is part of mbed TLS (https://tls.mbed.org)
# Builds all variants of all fuzz targets
#
# The Docker image has (currently) support for the following variants:
#
#  - fast       clang build with optimisations and instrumentation
#
#  - asan       clang build without optimisations, with coverage
#                 instrumentation and address sanitizer, also includes ubsan
#
#  - msan       clang build without optimisations, with coverage
#                 instrumentation and memory sanitizer
#
#  - afl        afl-clang-fast build
#
# The variants are linked with the corresponding variant build of mbedtls, and
# in the case of memory sanitizer with libc++.
#
# To list all fuzz targets, run make -f src/Makefile list_targets

.PHONY: clean install all list_targets

CC=clang
CXX=clang++

# TODO: these could do with some cleaning up
LIBS=-lmbedtls -lmbedx509 -lmbedcrypto
FUZZER_LIBS=-lFuzzer ${LIBS}
MSAN_FUZZER_LIBS=-lFuzzer_msan ${LIBS}
CFLAGS=-fsanitize-coverage=trace-pc-guard -I/usr/local/a/mbedtls/include/ -g -fno-omit-frame-pointer -O1 \
       -DFUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
LDFLAGS=-fsanitize-coverage=trace-pc-guard

ASAN_OPT=-fsanitize=address,undefined -fno-sanitize-recover=undefined
MSAN_OPT=-fsanitize=memory -fsanitize-memory-track-origins

ASAN_LIB=-L/usr/local/fat/mbedtls-libfuzzer-asan-ubsan/lib
MSAN_LIB=-L/usr/local/fat/mbedtls-libfuzzer-msan/lib -L/usr/local/libcxx_msan/lib
FAST_LIB=-L/usr/local/fat/mbedtls-libfuzzer-fast/lib
AFL_LIB=-L/usr/local/fat/mbedtls-afl/lib
COVERAGE_LIB=-L/usr/local/fat/mbedtls-libfuzzer-coverage/lib

AFL_CC=afl-clang-fast
AFL_CXX=afl-clang-fast++
AFL_LIBS=${LIBS}
AFL_CFLAGS=-I/usr/local/a/mbedtls/include/

TARGETS := csr_parse_der \
	   crt_parse_der \
	   crl_parse_der \
	   pk_parse_key \
	   pk_parse_public_key \
	   crt_verify \
	   dhm_parse_dhm \
	   handshake

# TODO: properly add ocsp_parse_response as a target once that feature has been
# committed.
#
# TARGETS += ocsp_parse_response

LIBFUZZER_VARIANTS := fast asan msan

RUNNERS = $(foreach T, $(TARGETS), $(foreach V, $(LIBFUZZER_VARIANTS), fuzz_$V_$T)) \
	  $(foreach T, $(TARGETS), $(foreach V, $(LIBFUZZER_VARIANTS), standalone_$V_$T)) \
	  $(foreach T, $(TARGETS), coverage_$T) \
	  $(foreach T, $(TARGETS), afl_$T)

all: ${RUNNERS}

list_targets:
	@echo ${TARGETS}

%_asan.o: %.c
	${CC} ${ASAN_OPT} ${CFLAGS} $< -c -o $@
%_msan.o: %.c
	${CC} ${MSAN_OPT} ${CFLAGS} $< -c -o $@
%_fast.o: %.c
	${CC} ${CFLAGS} $< -c -o $@
%_coverage.o: %.c
	${CC} -I/usr/local/a/mbedtls/include/ $< -c -o $@
%_coverage.o: %.cpp
	${CXX} $< -c -o $@

%_afl.o: %.c
	${AFL_CC} ${AFL_CFLAGS} $< -c -o $@

fuzz_asan_%: %_asan.o
	${CXX} ${ASAN_OPT} ${ASAN_LIB} ${LDFLAGS} $< ${FUZZER_LIBS} -o $@

fuzz_msan_%: %_msan.o
	${CXX} -stdlib=libc++ -L/usr/local/libcxx_msan/lib ${MSAN_OPT} ${MSAN_LIB} ${MSAN_CFLAGS} ${LDFLAGS} $< ${MSAN_FUZZER_LIBS} -lc++abi -o $@

fuzz_fast_%: %_fast.o
	${CXX} ${FAST_LIB} ${LDFLAGS} $< ${FUZZER_LIBS} -o $@

standalone_asan_%: %_asan.o StandaloneFuzzTargetMain.o
	${CXX} ${ASAN_OPT} ${ASAN_LIB} ${LDFLAGS} $+ ${LIBS} -o $@

standalone_msan_%: %_msan.o StandaloneFuzzTargetMain.o
	${CXX} ${MSAN_OPT} ${MSAN_LIB} ${MSAN_CFLAGS} ${LDFLAGS} $+ ${LIBS} -o $@

standalone_fast_%: %_fast.o StandaloneFuzzTargetMain.o
	${CXX} ${FAST_LIB} ${LDFLAGS} $+ ${LIBS} -o $@

coverage_%: %_coverage.o StandaloneFuzzTargetMain_coverage.o
	${CXX} ${COVERAGE_LIB} -fprofile-instr-generate -fcoverage-mapping $+ ${LIBS} -o $@

afl_driver.o: afl_driver.cpp
	${AFL_CXX} ${AFL_CFLAGS} $< -c -o $@

afl_%: afl_driver.o %_afl.o
	${AFL_CXX} ${AFL_LIB} $+ ${AFL_LIBS} -o $@

clean:
	rm ${RUNNERS}

install:
	mkdir ../bin
	install ${RUNNERS} ../bin
