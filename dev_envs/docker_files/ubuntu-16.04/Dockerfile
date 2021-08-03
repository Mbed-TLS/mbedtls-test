# ubuntu-16.04/Dockerfile
#
#  Copyright (c) 2018-2021, ARM Limited, All Rights Reserved
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
# This docker file is for creating the ubuntu-16.04 image that is used in the
# CI. It can also be used for reproducing and testing CI failures.

FROM ubuntu:16.04

ARG DEBIAN_FRONTEND=noninteractive
WORKDIR /opt/slave

# Support for i386:
# - for 32-bit builds+tests of Mbed TLS
# - required to install Arm Compiler 5.06 (armcc)
RUN dpkg --add-architecture i386

# Main apt-get call with all packages except those that have conflicts,
# handled below. One big alphabetised list, in order to avoid duplicates, with
# comments explaining why each package is needed.
RUN apt-get update -q && apt-get install -yq \
        # TODO: also installed from source, redundant?
        abi-dumper \
        # to build Mbed TLS
        clang \
        # to build Mbed TLS
        cmake \
        # to build Mbed TLS's documentation
        doxygen \
        # to cross-build Mbed TLS
        gcc-mingw-w64-i686 \
        # to check out Mbed TLS and others
        git \
        # to build Mbed TLS's documentation
        graphviz \
        # to measure code coverage of Mbed TLS
        lcov \
        # for 32-bit Mbed TLS testing and armcc
        libc6-i386 \
        # for 32-bit Mbed TLS testing and armcc
        libc6:i386 \
        # required by OpenSSL
        libgmp10 \
        # required by OpenSSL
        libgmp-dev \
        # to build GnuTLS
        libunistring-dev \
        # required by OpenSSL
        libp11-kit-dev \
        # for armcc
        libstdc++6:i386 \
        # needed for armcc (see locale-gen below)
        locales \
        # used by compat.sh and ssl-opt.sh
        lsof \
        # required by OpenSSL
        m4 \
        # to build Mbed TLS and others
        make \
        # TODO: do we need this?
        mercurial \
        # required by OpenSSL
        p11-kit \
        # required by OpenSSL
        pkg-config \
        # to install the preferred version of pylint
        python3-pip \
        # TODO: do we need this?
        subversion \
        # for Mbed TLS tests
        valgrind \
        # to download things installed from other places
        wget \
        # TODO: do we need this?
        zip \
        # to build Mbed TLS with MBEDTLS_ZILIB_SUPPORT (removed in 3.0)
        zlib1g \
        # to build Mbed TLS with MBEDTLS_ZILIB_SUPPORT (removed in 3.0)
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/

# Install all the parts of gcc-multilib, which is necessary for 32-bit builds.
# gcc-multilib conflicts with cross-compiler packages that we'll install later,
# so don't keep it around. Just let it install its dependencies
# (gcc-<VERSION>-multilib and libc support), then remove it. Manually create
# one crucial symlink that's otherwise provided by the gcc-multilib package
# (without that symlink, 32-bit builds won't find system headers). Note that
# just installing the dependencies of gcc-multilib also brings in gcc-multilib
# as a Recommends dependency.
RUN apt-get update -q && apt-get install -yq \
        gcc-multilib \
    && rm -rf /var/lib/apt/lists/ && \
    dpkg -r gcc-multilib && \
    ln -s x86_64-linux-gnu/asm /usr/include/asm

# Install arm-linux-gnueabi-gcc - to cross-build Mbed TLS
RUN apt-get update -q && apt-get install -yq \
        gcc-arm-linux-gnueabi \
        libc6-dev-armel-cross \
    && rm -rf /var/lib/apt/lists/

# Install ARM Compiler 5.06
RUN wget -q https://developer.arm.com/-/media/Files/downloads/compiler/DS500-PA-00003-r5p0-22rel0.tgz && \
    tar -zxf DS500-PA-00003-r5p0-22rel0.tgz && \
    ./Installer/setup.sh --i-agree-to-the-contained-eula --no-interactive -d /usr/local/ARM_Compiler_5.06u3 --quiet && \
    rm -rf DS500-PA-00003-r5p0-22rel0.tgz releasenotes.html Installer/

ENV ARMC5_BIN_DIR=/usr/local/ARM_Compiler_5.06u3/bin/
ENV PATH=$PATH:/usr/local/ARM_Compiler_5.06u3/bin
ENV ARMLMD_LICENSE_FILE=8225@armcc.licenses.mbedcloudtesting.com

# Install ARM Compiler 6.6
RUN mkdir temp && cd temp && \
    wget -q --no-check-certificate https://developer.arm.com/-/media/Files/downloads/compiler/DS500-BN-00026-r5p0-07rel0.tgz?revision=8f0d9fb0-9616-458c-b2f5-d0dac83ea93c?product=Downloads,64-bit,,Linux,6.6 -O arm6.tgz && \
    tar -zxf arm6.tgz  && ls -ltr && \
    ./install_x86_64.sh --i-agree-to-the-contained-eula --no-interactive -d /usr/local/ARM_Compiler_6.6 --quiet && \
    cd .. && rm -rf temp/

ENV ARMC6_BIN_DIR=/usr/local/ARM_Compiler_6.6/bin/

# Install arm-none-eabi-gcc
RUN wget -q https://developer.arm.com/-/media/Files/downloads/gnu-rm/5_4-2016q3/gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 -O gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 && \
    tar -xjf gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 -C /opt

ENV PATH=/opt/gcc-arm-none-eabi-5_4-2016q3/bin:$PATH

# Install openssl 1.0.2g
RUN wget -q https://www.openssl.org/source/old/1.0.2/openssl-1.0.2g.tar.gz && \
    tar -zxf openssl-1.0.2g.tar.gz && cd openssl-1.0.2g && \
    ./config --openssldir=/usr/local/openssl-1.0.2g && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.0.2g*

ENV OPENSSL=/usr/local/openssl-1.0.2g/bin/openssl
ENV PATH=/usr/local/openssl-1.0.2g/bin:$PATH

# Install openssl 1.0.1j for legacy testing
RUN wget -q https://www.openssl.org/source/old/1.0.1/openssl-1.0.1j.tar.gz && \
    tar -zxf openssl-1.0.1j.tar.gz && cd openssl-1.0.1j && \
    ./config --openssldir=/usr/local/openssl-1.0.1j && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.0.1j*

ENV OPENSSL_LEGACY=/usr/local/openssl-1.0.1j/bin/openssl

# Install openssl 1.1.1a for ARIA cipher testing
RUN wget -q https://www.openssl.org/source/openssl-1.1.1a.tar.gz && \
    tar -zxf openssl-1.1.1a.tar.gz && cd openssl-1.1.1a && \
    ./config --prefix=/usr/local/openssl-1.1.1a -Wl,--enable-new-dtags,-rpath,'$(LIBRPATH)' && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.1.1a*

ENV OPENSSL_NEXT=/usr/local/openssl-1.1.1a/bin/openssl

# Install Gnu TLS 3.4.10
RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.1.tar.gz && \
    tar -zxf nettle-3.1.tar.gz && cd nettle-3.1 && \
    ./configure --prefix=/usr/local/libnettle-3.1 --exec_prefix=/usr/local/libnettle-3.1  --disable-shared && \
    make && make install && cd .. && rm -rf nettle-3.1*

ENV PKG_CONFIG_PATH=/usr/local/libnettle-3.1/lib/pkgconfig:/usr/local/libnettle-3.1/lib64/pkgconfig:/usr/local/lib/pkgconfig

RUN wget -q https://ftp.gnu.org/gnu/libtasn1/libtasn1-4.3.tar.gz && \
    tar -zxf libtasn1-4.3.tar.gz && cd libtasn1-4.3 && \
    ./configure && make && make install && \
    cd .. && rm -rf libtasn1-4.3*

RUN wget -q https://github.com/p11-glue/p11-kit/releases/download/0.23.10/p11-kit-0.23.10.tar.gz && \
    tar -zxf p11-kit-0.23.10.tar.gz && cd p11-kit-0.23.10 && \
    ./configure --prefix=/usr/local/libp11-kit-0.23.10 && make && make install && \
    cd .. && rm -rf p11-kit-0.23.10*

ENV PKG_CONFIG_PATH=/usr/local/lib/libp11-kit-0.23.10/lib/pkgconfig:/usr/local/lib/libp11-kit-0.23.10/lib64/pkgconfig:$PKG_CONFIG_PATH

RUN wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.4/gnutls-3.4.10.tar.xz && \
    tar -xJf gnutls-3.4.10.tar.xz && cd gnutls-3.4.10 && \
    ./configure --prefix=/usr/local/gnutls-3.4.10 --exec_prefix=/usr/local/gnutls-3.4.10 --disable-shared && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.4.10*

ENV GNUTLS_CLI=/usr/local/gnutls-3.4.10/bin/gnutls-cli
ENV GNUTLS_SERV=/usr/local/gnutls-3.4.10/bin/gnutls-serv
ENV PATH=/usr/local/gnutls-3.4.10/bin:$PATH

# Install Gnu TLS 3.3.8 for legacy testing
RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-2.7.1.tar.gz && \
    tar -zxf nettle-2.7.1.tar.gz && cd nettle-2.7.1 && \
    ./configure --prefix=/usr/local/libnettle-2.7.1 --exec_prefix=/usr/local/libnettle-2.7.1  --disable-shared && \
    make && make install && cd .. && rm -rf nettle-2.7.1*

ENV PKG_CONFIG_PATH=/usr/local/libnettle-2.7.1/lib/pkgconfig:/usr/local/libnettle-2.7.1/lib64/pkgconfig:/usr/local/lib/pkgconfig

RUN wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.3/gnutls-3.3.8.tar.xz && \
    tar -xJf gnutls-3.3.8.tar.xz && cd gnutls-3.3.8 && \
    ./configure --prefix=/usr/local/gnutls-3.3.8 --exec_prefix=/usr/local/gnutls-3.3.8 --disable-shared && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.3.8*

ENV GNUTLS_LEGACY_CLI=/usr/local/gnutls-3.3.8/bin/gnutls-cli
ENV GNUTLS_LEGACY_SERV=/usr/local/gnutls-3.3.8/bin/gnutls-serv

# Instal GNU TLS 3.6.5 for broader interoperability testing
RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.4.1.tar.gz && \
    tar -zxf nettle-3.4.1.tar.gz && cd nettle-3.4.1 && \
    ./configure --prefix=/usr/local/libnettle-3.4.1 --exec_prefix=/usr/local/libnettle-3.4.1  --disable-shared && \
    make && make install && cd .. && rm -rf nettle-3.4.1*

RUN wget -q https://ftp.gnu.org/gnu/libtasn1/libtasn1-4.9.tar.gz && \
    tar -zxf libtasn1-4.9.tar.gz && cd libtasn1-4.9 && \
    ./configure && make && make install && \
    cd .. && rm -rf libtasn1-4.9*

ENV PKG_CONFIG_PATH=/usr/local/libnettle-3.4.1/lib/pkgconfig:/usr/local/libnettle-3.4.1/lib64/pkgconfig:/usr/local/lib/pkgconfig

RUN wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.6/gnutls-3.6.5.tar.xz && \
    tar -xJf gnutls-3.6.5.tar.xz && cd gnutls-3.6.5 && \
    ./configure --prefix=/usr/local/gnutls-3.6.5 --exec_prefix=/usr/local/gnutls-3.6.5 --disable-shared && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.6.5*

ENV GNUTLS_NEXT_CLI=/usr/local/gnutls-3.6.5/bin/gnutls-cli
ENV GNUTLS_NEXT_SERV=/usr/local/gnutls-3.6.5/bin/gnutls-serv

# Install abi-compliance-checker
RUN wget -q https://github.com/lvc/abi-compliance-checker/archive/2.3.tar.gz && \
    tar -zxf 2.3.tar.gz && cd abi-compliance-checker-2.3 && \
    make clean && make && make install prefix=/usr && cd .. && \
    rm -rf openssl-1.0.2g* && rm 2.3.tar.gz

# Install abi-dumper
RUN git clone --branch 1.1 https://github.com/lvc/abi-dumper.git && \
    cd abi-dumper && make install prefix=/usr && cd .. && rm -rf abi-dumper

# Install Python pip packages
#
# The pip wrapper scripts can get out of sync with pip due to upgrading it
# outside the package manager, so invoke the module directly.
#
# Ubuntu 16.04's pip (8.1) doesn't understand the Requires-Python
# directive (introduced in pip 9.0), and tries to install the wrong versions
# of pip and setuptools. Version 21 of pip drops support for Python 3.5 (the
# latest in 16.04), so pick an earlier version.
#
# Piping to cat suppresses the progress bar. Then when we have a more recent
# pip (>= 10) we can use its command-line option for that.
RUN python3 -m pip install 'pip<21' --upgrade | cat && \
    python3 -m pip install --progress-bar=off setuptools --upgrade && \
    python3 -m pip install --progress-bar=off pylint==2.4.4

# Set locale for ARMCC to work
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

