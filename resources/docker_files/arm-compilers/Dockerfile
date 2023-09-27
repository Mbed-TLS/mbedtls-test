# syntax=docker/dockerfile:1
#
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

# For now, copy the ARM compilers from a fixed image which is present in our caches.
# This avoids the problem that the download URLs for Arm compilers are
# not longer valid.
ARG DOCKER_REPO
FROM $DOCKER_REPO:arm-compilers-c568d022eecd6e6263f7bc0f8e94e003dd652160 AS arm-compiler-binaries

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
        # to build Mbed TLS: gcc, binutils, make, etc.
        build-essential \
        # to build Mbed TLS
        cmake \
        # to check out Mbed TLS and others
        git \
        # for 32-bit Mbed TLS testing and armcc
        libc6-i386 \
        # for 32-bit Mbed TLS testing and armcc
        libc6:i386 \
        # for armcc
        libstdc++6:i386 \
        # needed for armcc (see locale-gen below)
        locales \
        # to install several Python packages (done by individual jobs)
        python3-pip \
    && rm -rf /var/lib/apt/lists/

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

# Install ARM Compiler 5.06 and 6.6
COPY --link --from=arm-compiler-binaries /usr/local/ARM_Compiler_5.06u3 /usr/local/ARM_Compiler_5.06u3
COPY --link --from=arm-compiler-binaries /usr/local/ARM_Compiler_6.6 /usr/local/ARM_Compiler_6.6

ENV ARMC5_BIN_DIR=/usr/local/ARM_Compiler_5.06u3/bin/
ENV ARMC6_BIN_DIR=/usr/local/ARM_Compiler_6.6/bin/
ENV PATH=$PATH:/usr/local/ARM_Compiler_5.06u3/bin
ARG ARMLMD_LICENSE_FILE=7010@10.6.26.52:7010@10.6.26.53:7010@10.6.26.54:7010@10.6.26.56
ENV ARMLMD_LICENSE_FILE=${ARMLMD_LICENSE_FILE}

# Add user
RUN useradd -m user

# Create workspace
ARG AGENT_WORKDIR=/var/lib/builds
RUN mkdir -p ${AGENT_WORKDIR} && chown user:user ${AGENT_WORKDIR}
USER user
ENV AGENT_WORKDIR=${AGENT_WORKDIR}

WORKDIR ${AGENT_WORKDIR}

ENTRYPOINT ["bash"]

