# ubuntu-24.04-nodepends/Dockerfile
#
#  Copyright (c) 2018-2023, ARM Limited, All Rights Reserved
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
# Minimal Ubuntu 24.04 image to build Mbed TLS from source tarballs.
# This intentionally excludes Python, Perl and CI-only dependencies.

FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive
WORKDIR /opt/src

# Note: scripts/min_requirements.py need a writable
# destination for installing python dependencies
# FIXME: This should be /var/lib/build to use the host src directory as HOME
ENV HOME=/var/lib/builds

# Support for 32-bit builds+tests of Mbed TLS
RUN case "$(uname -m)" in \
        x86_64)  dpkg --add-architecture i386;; \
        aarch64) dpkg --add-architecture armhf;; \
    esac

RUN apt-get update -q && apt-get install -yq \
        # to build Mbed TLS: gcc, binutils, make, etc.
        build-essential \
        # to have a UTF-8 locale (see locale-gen below)
        locales \
        # to build Mbed TLS
        clang \
        # to build Mbed TLS
        cmake \
        make \
        # to cross-build Mbed TLS
        gcc-mingw-w64-i686 \
        # to build Mbed TLS using latest gcc version available from Ubuntu
        gcc-14 \
        # build dependency of GCC
        libisl-dev libmpfr-dev libmpc-dev \
        # to check out Mbed TLS and others
        git \
        # to download things installed from other places
        wget \
        # to build Mbed TLS with MBEDTLS_ZILIB_SUPPORT (removed in 3.0)
        zlib1g \
        # to build Mbed TLS with MBEDTLS_ZILIB_SUPPORT (removed in 3.0)
        zlib1g-dev \
        # Reliable extraction of .tar.gz and .tar.xz source bundles.
        tar \
        xz-utils \
        && rm -rf /var/lib/apt/lists/

# Make sure we have a UTF-8 locale
RUN locale && \
    locale-gen "en_US.UTF-8" && \
    dpkg-reconfigure locales

# Add user
RUN useradd -m user

# Create workspace
# FIXME: This should be /var/lib/build to use the host src directory as HOME
ARG AGENT_WORKDIR=/var/lib/builds
ENV AGENT_WORKDIR=${AGENT_WORKDIR}
RUN mkdir -p ${AGENT_WORKDIR} && chown -R user:user ${AGENT_WORKDIR}

# From now on, run as the unprivileged user.
USER user
WORKDIR ${AGENT_WORKDIR}

RUN \
    # Make it possible to do git commits, e.g. to test prepare_release.py.
    git config --global user.name 'Contained User' && \
    git config --global user.email 'contained.user@unknown.invalid' && \
    :

ENTRYPOINT ["bash"]
