# arm-compilers/Dockerfile
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
# This docker file is for creating the Linux image that is used in the
# CI with proprietary Arm compilers. It can also be used for reproducing and
# testing CI failures if you have access to the Arm internal license servers,
# or if you have access to different license servers by overriding the
# ARMLMD_LICENSE_FILE argument when building the image
# (docker build --build-arg ARMLMD_LICENSE_FILE=... arm-compilers).

FROM ubuntu:20.04

ARG DEBIAN_FRONTEND=noninteractive
WORKDIR /opt/src

# Note: scripts/min_requirements.py need a writable
# destination for installing python dependencies
ENV HOME=/var/lib/builds

# Support for i386:
# - for 32-bit builds+tests of Mbed TLS
# - required to install Arm Compiler 5.06 (armcc)
RUN dpkg --add-architecture i386

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
        # to build Mbed TLS: gcc, binutils, make, etc.
        build-essential \
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
        # to build GnuTLS (nettle with public key support aka hogweed)
        libgmp-dev \
        # to build GnuTLS >= 3.6 (could also use --with-included-unistring)
        libunistring-dev \
        # for armcc
        libstdc++6:i386 \
        # to build GnuTLS
        libtasn1-6-dev \
        # needed for armcc (see locale-gen below)
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
RUN wget -q https://armkeil.blob.core.windows.net/developer/Files/downloads/compiler/DS500-PA-00003-r5p0-22rel0.tgz && \
    tar -zxf DS500-PA-00003-r5p0-22rel0.tgz && \
    ./Installer/setup.sh --i-agree-to-the-contained-eula --no-interactive -d /usr/local/ARM_Compiler_5.06u3 --quiet && \
    rm -rf DS500-PA-00003-r5p0-22rel0.tgz releasenotes.html Installer/

ENV ARMC5_BIN_DIR=/usr/local/ARM_Compiler_5.06u3/bin/
ENV PATH=$PATH:/usr/local/ARM_Compiler_5.06u3/bin
ARG ARMLMD_LICENSE_FILE=7010@10.6.26.52:7010@10.6.26.53:7010@10.6.26.54:7010@10.6.26.56
ENV ARMLMD_LICENSE_FILE=${ARMLMD_LICENSE_FILE}

# Install ARM Compiler 6.6
RUN mkdir temp && cd temp && \
    wget -q --no-check-certificate https://armkeil.blob.core.windows.net/developer//sitecore/shell/-/media/Files/downloads/compiler/DS500-BN-00026-r5p0-07rel0.tgz -O arm6.tgz && \
    tar -zxf arm6.tgz  && ls -ltr && \
    ./install_x86_64.sh --i-agree-to-the-contained-eula --no-interactive -d /usr/local/ARM_Compiler_6.6 --quiet && \
    cd .. && rm -rf temp/

ENV ARMC6_BIN_DIR=/usr/local/ARM_Compiler_6.6/bin/

# Install arm-none-eabi-gcc
RUN wget -q https://developer.arm.com/-/media/Files/downloads/gnu-rm/5_4-2016q3/gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 -O gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 && \
    tar -xjf gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 -C /opt && \
    rm gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2

ENV PATH=/opt/gcc-arm-none-eabi-5_4-2016q3/bin:$PATH

# Install Python pip packages
#
# The pip wrapper scripts can get out of sync with pip due to upgrading it
# outside the package manager, so invoke the module directly.
RUN python3 -m pip config set global.progress_bar off && \
    python3 -m pip install setuptools --upgrade && \
    true

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

