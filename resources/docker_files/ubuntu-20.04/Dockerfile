# ubuntu-20.04/Dockerfile
#
#  Copyright (c) 2018-2022, ARM Limited, All Rights Reserved
#  SPDX-License-Identifier: Apache-2.0
#
#  Licensed under the Apache License, Version 2.0 (the "License"); you may
#  not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)

# Purpose
# -------
#
# This docker file is for creating the ubuntu-20.04 image that is used in the
# CI. It can also be used for reproducing and testing CI failures.

FROM ubuntu:20.04

ARG DEBIAN_FRONTEND=noninteractive
WORKDIR /opt/src

# Note: scripts/min_requirements.py need a writable
# destination for installing python dependencies
ENV HOME=/var/lib/builds

# Support for 32-bit builds+tests of Mbed TLS
RUN case "$(uname -m)" in \
        x86_64)  dpkg --add-architecture i386;; \
        aarch64) dpkg --add-architecture armhf;; \
    esac

# Main apt-get call with all packages except those that have conflicts,
# handled below. One big alphabetised list, in order to avoid duplicates, with
# comments explaining why each package is needed.
RUN apt-get update -q && apt-get install -yq \
        # for Mbed TLS tests
        abi-compliance-checker \
        # Note that there is a known issue #5332 that stock abi tools
        # in ubuntu20.04 do not fail as expected.
        # https://github.com/ARMmbed/mbedtls/issues/5332
        # Do not activae 20.04 until that is resolved
        # to use with abi-compliance-tester
        abi-dumper \
        # to update the autoconf files in nettle 2.7 with support for aarch64
        autotools-dev \
        # to build Mbed TLS: gcc, binutils, make, etc.
        build-essential \
        # to generate malformed files
        bsdmainutils \
        # to build Mbed TLS
        clang \
        # to build Mbed TLS
        cmake \
        # to build Mbed TLS's documentation
        doxygen \
        # to run tests in specific time.
        faketime \
        # to cross-build Mbed TLS
        gcc-mingw-w64-i686 \
        # to check out Mbed TLS and others
        git \
        # to build Mbed TLS's documentation
        graphviz \
        # to measure code coverage of Mbed TLS
        lcov \
        # to build GnuTLS (nettle with public key support aka hogweed)
        libgmp-dev \
        # to build GnuTLS >= 3.6 (could also use --with-included-unistring)
        libunistring-dev \
        # to build GnuTLS
        libtasn1-6-dev \
        # to have a UTF-8 locale (see locale-gen below)
        locales \
        # used by compat.sh and ssl-opt.sh
        lsof \
        # to build GnuTLS (nettle)
        m4 \
        # to build Mbed TLS and others
        make \
        # to build GnuTLS with locally-compiled nettle
        pkg-config \
        # to install several Python packages (done by individual jobs)
        python3-pip \
        # for Mbed TLS tests
        valgrind \
        # to download things installed from other places
        wget \
        # for data file generating
        xxd \
        # to build Mbed TLS with MBEDTLS_ZILIB_SUPPORT (removed in 3.0)
        zlib1g \
        # to build Mbed TLS with MBEDTLS_ZILIB_SUPPORT (removed in 3.0)
        zlib1g-dev \
    && case "$(uname -m)" in \
        # x86_64 only packages
        x86_64) apt-get install -yq \
            # for 32-bit Mbed TLS testing
            libc6-i386 \
            # for 32-bit Mbed TLS testing
            libc6:i386 \
            # to test 32-bit C++ linkage (not done at the time of writing)
            libstdc++6:i386 \
            ;; \
        # aarch64 only packages
        aarch64) apt-get install -yq \
            # for 32-bit Mbed TLS testing
            libc6:armhf \
            # to test 32-bit C++ linkage (not done at the time of writing)
            libstdc++6:armhf \
            ;; \
    esac && \
    rm -rf /var/lib/apt/lists/

# Install all the parts of gcc-multilib, which is necessary for 32-bit builds.
# gcc-multilib conflicts with cross-compiler packages that we'll install later,
# so don't keep it around. Just let it install its dependencies
# (gcc-<VERSION>-multilib and libc support), then remove it. Manually create
# one crucial symlink that's otherwise provided by the gcc-multilib package
# (without that symlink, 32-bit builds won't find system headers). Note that
# just installing the dependencies of gcc-multilib also brings in gcc-multilib
# as a Recommends dependency.
RUN if [ "$(uname -m)" = x86_64 ]; then \
        apt-get update -q && apt-get install -yq \
            gcc-multilib \
        && rm -rf /var/lib/apt/lists/ && \
        dpkg -r gcc-multilib && \
        ln -s x86_64-linux-gnu/asm /usr/include/asm; \
    fi

# Install arm-linux-gnueabi-gcc and arm-none-eabi-gcc - to cross-build Mbed TLS
RUN if [ "$(uname -m)" = aarch64 ]; then \
        # HACK: Ubuntu doesn't provide an armel port for Thumb-1 testing, so link
        #       the cross-compiler's libraries into the standard multiarch location.
        #       We need to create and link this directory first due to the /usr merge
        #       from Ubuntu 19.04 onward.
        mkdir -p /usr/arm-linux-gnueabi/lib && \
        ln -s /usr/arm-linux-gnueabi/lib /lib/arm-linux-gnueabi && \
        ln -s arm-linux-gnueabi/ld-linux.so.3 /lib/ld-linux.so.3; \
    fi && \
    apt-get update -q && apt-get install -yq \
        gcc-arm-linux-gnueabi \
        gcc-arm-none-eabi \
        libc6-dev-armel-cross \
    && rm -rf /var/lib/apt/lists/

# Install exact upstream versions of OpenSSL and GnuTLS
#
# Distro packages tend to include patches that disrupt our testing scripts,
# and such patches may be added at any time. Avoid surprises by using fixed
# versions.

RUN wget -q https://www.openssl.org/source/old/1.0.1/openssl-1.0.1j.tar.gz && \
    tar -zxf openssl-1.0.1j.tar.gz && cd openssl-1.0.1j && \
    ./config --openssldir=/usr/local/openssl-1.0.1j && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.0.1j*
ENV OPENSSL_1_0_1=/usr/local/openssl-1.0.1j/bin/openssl
# Up to at least 2023, the test scripts expect that $OPENSSL_LEGACY is 1.0.1j.
ENV OPENSSL_LEGACY=/usr/local/openssl-1.0.1j/bin/openssl

RUN wget -q https://www.openssl.org/source/old/1.0.2/openssl-1.0.2g.tar.gz && \
    tar -zxf openssl-1.0.2g.tar.gz && cd openssl-1.0.2g && \
    ./config --openssldir=/usr/local/openssl-1.0.2g enable-ssl-trace && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.0.2g*
ENV OPENSSL_1_0_2=/usr/local/openssl-1.0.2g/bin/openssl
# Up to at least 2023, the test scripts expect that $OPENSSL is 1.0.2g.
ENV OPENSSL=/usr/local/openssl-1.0.2g/bin/openssl

RUN wget -q https://www.openssl.org/source/openssl-1.1.1a.tar.gz && \
    tar -zxf openssl-1.1.1a.tar.gz && cd openssl-1.1.1a && \
    ./config --prefix=/usr/local/openssl-1.1.1a -Wl,--enable-new-dtags,-rpath,'$(LIBRPATH)' enable-ssl-trace && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.1.1a*
ENV OPENSSL_1_1_1=/usr/local/openssl-1.1.1a/bin/openssl
ENV OPENSSL_1_1=/usr/local/openssl-1.1.1a/bin/openssl
ENV OPENSSL_1=/usr/local/openssl-1.1.1a/bin/openssl
# Up to at least 2023, the test scripts expect that $OPENSSL_NEXT is 1.1.1a.
ENV OPENSSL_NEXT=/usr/local/openssl-1.1.1a/bin/openssl

# GnuTLS has a number of (optional) dependencies:
# - nettle (crypto library): quite tighly coupled, so build one for each
# version of GnuTLS that we want.
# - libtasn1: the Ubuntu package works; if it didn't a config
# option --with-included-libtasn1 is available.
# - p11-kit: optional, for smart-card support - configure it out
# - libunistring: since 3.6 - the Ubuntu package works; if it didn't a config
# option --with-included-libunistring is available.

# Install Gnu TLS 3.4.10 (nettle 3.1) - main version, in the PATH
RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.1.tar.gz && \
    tar -zxf nettle-3.1.tar.gz && cd nettle-3.1 && \
    ./configure --prefix=/usr/local/libnettle-3.1 --exec_prefix=/usr/local/libnettle-3.1  --disable-shared --disable-openssl && \
    make && make install && cd .. && rm -rf nettle-3.1* && \
    export PKG_CONFIG_PATH=/usr/local/libnettle-3.1/lib/pkgconfig:/usr/local/libnettle-3.1/lib64/pkgconfig:/usr/local/lib/pkgconfig && \
    wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.4/gnutls-3.4.10.tar.xz && \
    tar -xJf gnutls-3.4.10.tar.xz && cd gnutls-3.4.10 && \
    ./configure --prefix=/usr/local/gnutls-3.4.10 --exec_prefix=/usr/local/gnutls-3.4.10 --disable-shared --without-p11-kit && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.4.10*
ENV GNUTLS_3_4_10_CLI=/usr/local/gnutls-3.4.10/bin/gnutls-cli
ENV GNUTLS_3_4_10_SERV=/usr/local/gnutls-3.4.10/bin/gnutls-serv
ENV GNUTLS_3_4_CLI=/usr/local/gnutls-3.4.10/bin/gnutls-cli
ENV GNUTLS_3_4_SERV=/usr/local/gnutls-3.4.10/bin/gnutls-serv
# Up to at least 2023, the test scripts expect that $GNUTLS_xxx is 3.4.10.
ENV GNUTLS_CLI=/usr/local/gnutls-3.4.10/bin/gnutls-cli
ENV GNUTLS_SERV=/usr/local/gnutls-3.4.10/bin/gnutls-serv

# For backward compatibility with older all.sh. At the time of writing,
# GnuTLS 3.4.10 works fine as the "legacy" version, and newer all.sh will
# stop using a separate GNUTLS_LEGACY_xxx pair of programs.
ENV GNUTLS_LEGACY_CLI=$GNUTLS_CLI
ENV GNUTLS_LEGACY_SERV=$GNUTLS_SERV

# Instal GNU TLS 3.7.2 (nettle 3.7) - "next" version
RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.7.3.tar.gz && \
    tar -zxf nettle-3.7.3.tar.gz && cd nettle-3.7.3 && \
    ./configure --prefix=/usr/local/libnettle-3.7.3 --exec_prefix=/usr/local/libnettle-3.7.3  --disable-shared --disable-openssl && \
    make && make install && cd .. && rm -rf nettle-3.7.3* && \
    export PKG_CONFIG_PATH=/usr/local/libnettle-3.7.3/lib/pkgconfig:/usr/local/libnettle-3.7.3/lib64/pkgconfig:/usr/local/lib/pkgconfig && \
    wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.7/gnutls-3.7.2.tar.xz && \
    tar -xJf gnutls-3.7.2.tar.xz && cd gnutls-3.7.2 && \
    ./configure --prefix=/usr/local/gnutls-3.7.2 --exec_prefix=/usr/local/gnutls-3.7.2 --disable-shared --without-p11-kit && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.7.2*
ENV GNUTLS_3_7_2_CLI=/usr/local/gnutls-3.7.2/bin/gnutls-cli
ENV GNUTLS_3_7_2_SERV=/usr/local/gnutls-3.7.2/bin/gnutls-serv
ENV GNUTLS_3_7_CLI=/usr/local/gnutls-3.7.2/bin/gnutls-cli
ENV GNUTLS_3_7_SERV=/usr/local/gnutls-3.7.2/bin/gnutls-serv
ENV GNUTLS_3_CLI=/usr/local/gnutls-3.7.2/bin/gnutls-cli
ENV GNUTLS_3_SERV=/usr/local/gnutls-3.7.2/bin/gnutls-serv
# Up to at least 2023, the test scripts expect that $GNUTLS_NEXT_xxx is 3.7.2.
ENV GNUTLS_NEXT_CLI=/usr/local/gnutls-3.7.2/bin/gnutls-cli
ENV GNUTLS_NEXT_SERV=/usr/local/gnutls-3.7.2/bin/gnutls-serv

# Install uncrustify
# We need version 0.75.1
RUN git clone --branch uncrustify-0.75.1 https://github.com/uncrustify/uncrustify.git && \
    cd uncrustify && mkdir build && cd build && cmake .. && make install && \
    cd .. && rm -rf uncrustify

# Install Python pip packages
#
# The pip wrapper scripts can get out of sync with pip due to upgrading it
# outside the package manager, so invoke the module directly.
RUN python3 -m pip config set global.progress_bar off && \
    python3 -m pip install setuptools --upgrade && \
    true

# Make sure we have a UTF-8 locale
RUN locale && \
    locale-gen "en_US.UTF-8" && \
    dpkg-reconfigure locales

# Add user
RUN useradd -m user

# Create workspace
ARG AGENT_WORKDIR=/var/lib/builds
RUN mkdir -p ${AGENT_WORKDIR} && chown user:user ${AGENT_WORKDIR}
USER user
ENV AGENT_WORKDIR=${AGENT_WORKDIR}

WORKDIR ${AGENT_WORKDIR}

ENTRYPOINT ["bash"]

