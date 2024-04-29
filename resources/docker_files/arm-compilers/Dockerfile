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

# Download and verify hash of Arm Compiler 6 tarball
# The URL is provided as a secret, so changing it doesn't trigger a rebuild of the image
# On the other hand, the hash is provided as a build argument, so changing it *does* trigger a rebuild.
FROM curlimages/curl-base:8.7.1 AS armc5

# Hash of ARMCompiler_506_Linux_x86_b960.tar.gz - override if you use a different compiler version
ARG ARMC5_SHA256=85d741527ba91383e419d86be330112d8e9e056adf3ba83d071d5be158ae3b4a

# The build context is mounted under /run/context so you can supply your own local tarball of Arm Compiler 5
# by pointing curl to file:///run/context/<tarball>
RUN --mount=type=secret,id=ARMC5_URL,required=true \
    --mount=type=bind,target=/run/context \
    { curl "$(cat /run/secrets/ARMC5_URL)" | tee /dev/fd/3 | tar -zx ./Installer/setup.sh; } 3>&1 | \
    { echo "$ARMC5_SHA256  /dev/fd/3" | sha256sum -c; } 3<&0

RUN ./Installer/setup.sh -d ./armc5 --i-agree-to-the-contained-eula --no-interactive --quiet

FROM curlimages/curl-base:8.7.1 AS armc6

# Hash of ARMCompiler6.21_standalone_linux-x86_64.tar.gz - override if you use a different compiler version
ARG ARMC6_SHA256=4bcdf9f719a8140b152b699fce8242a68b612cb0cb59f811ae11eb48302d1efe

# The build context is mounted under /run/context so you can supply your own local tarball of Arm Compiler 6
# by pointing curl to file:///run/context/<tarball>
RUN --mount=type=secret,id=ARMC6_URL,required=true \
    --mount=type=bind,target=/run/context \
    { curl "$(cat /run/secrets/ARMC6_URL)" | tee /dev/fd/3 | tar --exclude license_terms --exclude releasenotes.html -zx; } 3>&1 | \
    { echo "$ARMC6_SHA256  /dev/fd/3" | sha256sum -c; } 3<&0

RUN ./install_*.sh -d ./armc6 --i-agree-to-the-contained-eula --no-interactive --quiet

FROM ubuntu:20.04

ARG DEBIAN_FRONTEND=noninteractive
WORKDIR /opt/src

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
    # Ubuntu 20.04 ships pip 20.0.2. We want the ability to specify both
    # a versioned requirement and an unversioned requirement for the
    # same package (e.g. `pip install foo bar foo==42`), and this is
    # only possible since pip 20.3. So upgrade pip to the latest
    # release at the time of writing (use a fixed version to avoid
    # surprises when rebuilding the image).
    python3 -m pip install 'pip==23.2.1' && \
    python3 -m pip install setuptools --upgrade && \
    true

# Set locale for ARMCC to work
RUN locale && \
    locale-gen "en_US.UTF-8" && \
    dpkg-reconfigure locales

# Install ARM Compiler 5.06 and 6.21
ARG ARMC5_INSTALL_DIR=/usr/local/ARM_Compiler_5.06u7
ARG ARMC6_INSTALL_DIR=/usr/local/ARM_Compiler_6.21
COPY --link --from=armc5 --chown=root /home/curl_user/armc5 $ARMC5_INSTALL_DIR
COPY --link --from=armc6 --chown=root /home/curl_user/armc6 $ARMC6_INSTALL_DIR

ENV ARMC5_BIN_DIR=$ARMC5_INSTALL_DIR/bin/
ENV ARMC6_BIN_DIR=$ARMC6_INSTALL_DIR/bin/
ENV PATH=$PATH:$ARMC5_BIN_DIR
ARG ARMLMD_LICENSE_FILE=7010@10.6.26.52:7010@10.6.26.53:7010@10.6.26.54:7010@10.6.26.56
ENV ARMLMD_LICENSE_FILE=${ARMLMD_LICENSE_FILE}

# Add user
RUN useradd -m user

# Create workspace
ARG AGENT_WORKDIR=/var/lib/build
RUN mkdir -p ${AGENT_WORKDIR} && chown user:user ${AGENT_WORKDIR}
USER user
ENV AGENT_WORKDIR=${AGENT_WORKDIR}
# Note: scripts/min_requirements.py need a writable
# destination for installing python dependencies
ENV HOME=${AGENT_WORKDIR}

WORKDIR ${AGENT_WORKDIR}

ENTRYPOINT ["bash"]
