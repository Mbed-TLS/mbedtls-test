# debian-9-i386/Dockerfile
#
# This file is part of mbed TLS (https://tls.mbed.org)
#
# Copyright (c) 2018, ARM Limited, All Rights Reserved



################################################################
#### Documentation
################################################################

# Purpose
# -------
#
# This docker file is for creating a debian-i386 platform container. It contains
# setup and installation of tools for executing same set of Mbed TLS tests as
# there are in the CI. This conatiner can be used for reproducing and testing
# failures found in the CI.


#Start with basic Debian 32 bit image
FROM i386/debian:stretch

ENV DEBIAN_FRONTEND=noninteractive

# Install necessary apt tools
RUN apt-get update > /dev/null && \
    apt-get install -y apt-transport-https \
                       apt-utils \
                       software-properties-common dirmngr lsof > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# set the working directory to /opt/slave
WORKDIR /opt/slave

# Install source control tools
RUN apt-get update > /dev/null && \
    apt-get install -y git mercurial subversion wget > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/


# Install Python 2.7 
RUN apt-get update > /dev/null && \
    apt-get install -y python2.7 python-dev libffi-dev \
                       python-pip python-setuptools python-distutils-extra > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install Perl
RUN apt-get update > /dev/null && \
    apt-get install -y perl > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install make tools
RUN apt-get update > /dev/null && \
    apt-get install -y cmake make ninja-build > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install arm-none-eabi-gcc
RUN wget -q https://developer.arm.com/-/media/Files/downloads/gnu-rm/5_4-2016q3/gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 -O gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 && \
    tar -xjf gcc-arm-none-eabi-5_4-2016q3-20160926-linux.tar.bz2 -C /opt

# Install misc tools
RUN apt-get update > /dev/null && \
    apt-get install -y valgrind graphviz lcov gnutls-bin abi-dumper gcc-mingw-w64-i686 > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install openssl and GnuTLS
RUN apt-get update > /dev/null && \
    apt-get install -y gcc-multilib p11-kit libgmp10 libgmp-dev pkg-config m4 libp11-kit-dev > /dev/null && apt-get clean && rm -rf /var/lib/apt/lists/

RUN wget -q https://www.openssl.org/source/old/1.0.2/openssl-1.0.2g.tar.gz && \
    tar -zxf openssl-1.0.2g.tar.gz && cd openssl-1.0.2g && \
    ./config --openssldir=/usr/local/openssl-1.0.2g && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.0.2g*


RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.1.tar.gz && \
    tar -zxf nettle-3.1.tar.gz && cd nettle-3.1 && \
    ./configure --prefix=/usr/local/libnettle-3.1 --exec_prefix=/usr/local/libnettle-3.1  --disable-shared && \
    make && make install && cd .. && rm -rf nettle-3.1*

ENV PKG_CONFIG_PATH=/usr/local/libnettle-3.1/lib/pkgconfig:/usr/local/lib/pkgconfig

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
    ./configure --host=i686 --prefix=/usr/local/gnutls-3.4.10 --exec_prefix=/usr/local/gnutls-3.4.10 --disable-shared && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.4.10*

# Install GNU TLS 3.6.5 for broader interoperability testing
RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.4.1.tar.gz && \
    tar -zxf nettle-3.4.1.tar.gz && cd nettle-3.4.1 && \
    ./configure --prefix=/usr/local/libnettle-3.4.1 --exec_prefix=/usr/local/libnettle-3.4.1  --disable-shared && \
    make && make install && cd .. && rm -rf nettle-3.4.1*

RUN wget -q https://ftp.gnu.org/gnu/libtasn1/libtasn1-4.9.tar.gz && \
    tar -zxf libtasn1-4.9.tar.gz && cd libtasn1-4.9 && \
    ./configure && make && make install && \
    cd .. && rm -rf libtasn1-4.9*

ENV PKG_CONFIG_PATH=/usr/local/libnettle-3.4.1/lib/pkgconfig:/usr/local/libnettle-3.4.1/lib64/pkgconfig:/usr/local/lib/pkgconfig

RUN apt-get update > /dev/null && \
    apt-get install -y libunistring-dev > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

RUN wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.6/gnutls-3.6.5.tar.xz && \
    tar -xJf gnutls-3.6.5.tar.xz && cd gnutls-3.6.5 && \
    ./configure --host=i686 --prefix=/usr/local/gnutls-3.6.5 --exec_prefix=/usr/local/gnutls-3.6.5 --disable-shared && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.6.5*

ENV GNUTLS_NEXT_CLI=/usr/local/gnutls-3.6.5/bin/gnutls-cli
ENV GNUTLS_NEXT_SERV=/usr/local/gnutls-3.6.5/bin/gnutls-serv

# Remove unattended-upgrades to prevent unwanted changes to system whlei it is running
RUN apt-get purge -y unattended-upgrades

# Install mbed-cli
RUN pip install -U mbed-cli && \
    wget https://raw.githubusercontent.com/ARMmbed/mbed-os/master/requirements.txt -O mbed-os-requirements.txt && \
    pip install -r mbed-os-requirements.txt

# Install latest clang
RUN apt-get update > /dev/null && \
    apt-get install -y clang && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Add user
RUN useradd -m user

# Create workspace
ARG AGENT_WORKDIR=/var/lib/builds
RUN mkdir -p ${AGENT_WORKDIR} && chown user:user ${AGENT_WORKDIR}                                                                                                   

USER user
ENV AGENT_WORKDIR=${AGENT_WORKDIR}
ENV PATH=/opt/gcc-arm-none-eabi-5_4-2016q3/bin:/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:$PATH


WORKDIR ${AGENT_WORKDIR}


ENTRYPOINT ["bash"]

