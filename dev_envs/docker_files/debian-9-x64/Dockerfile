#Start with debian stretch
FROM debian:stretch

ENV DEBIAN_FRONTEND=noninteractive

# Install necessary apt tools
RUN apt-get update > /dev/null && \
    apt-get install -y apt-transport-https apt-utils software-properties-common dirmngr lsof > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# set the working directory to /opt/slave
WORKDIR /opt/slave

# Pre-approve Oracle Java license
RUN echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections

# Install source control tools
RUN apt-get update > /dev/null && \
    apt-get install -y git mercurial subversion > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install Python 2.7 
RUN apt-get update > /dev/null && \
    apt-get install -y python2.7 python-dev libffi-dev python-pip python-setuptools python-distutils-extra > /dev/null && \
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
RUN apt-get update > /dev/null && \
    apt-get install -y binutils-arm-none-eabi > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install Docker
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add - && \
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian $(lsb_release -cs) stable" && \
    apt-get update > /dev/null && \
    apt-get install -y docker-ce > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install misc tools
RUN apt-get update > /dev/null && \
    apt-get install -y wget valgrind graphviz lcov gnutls-bin abi-dumper gcc-mingw-w64-i686 > /dev/null && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Install openssl and GnuTLS
RUN wget -q https://www.openssl.org/source/old/1.0.2/openssl-1.0.2g.tar.gz && \
    tar -zxf openssl-1.0.2g.tar.gz && cd openssl-1.0.2g && \
    ./config --openssldir=/usr/local/openssl-1.0.2g && \
    make clean && make && make install && cd .. && \
    rm -rf openssl-1.0.2g*

RUN apt-get update > /dev/null && \
    apt-get install -y libtasn1-dev libgmp10 libgmp-dev pkg-config m4 libp11-kit-dev > /dev/null && \
   apt-get clean && rm -rf /var/lib/apt/lists/

RUN wget -q https://ftp.gnu.org/gnu/nettle/nettle-3.1.tar.gz && \
    tar -zxf nettle-3.1.tar.gz && cd nettle-3.1 && \
    ./configure --prefix=/usr/local/libnettle-3.1 --exec_prefix=/usr/local/libnettle-3.1  --disable-shared && \
    make && make install && cd .. && rm -rf nettle-3.1* 

ENV PKG_CONFIG_PATH=/usr/local/libnettle-3.1/lib/pkgconfig:/usr/local/libnettle-3.1/lib64/pkgconfig:/usr/local/lib/pkgconfig

RUN wget -q https://www.gnupg.org/ftp/gcrypt/gnutls/v3.4/gnutls-3.4.10.tar.xz && \
    tar -xJf gnutls-3.4.10.tar.xz && cd gnutls-3.4.10 && \
    ./configure --prefix=/usr/local/gnutls-3.4.10 --exec_prefix=/usr/local/gnutls-3.4.10 --disable-shared && \
    make && make install && cat config.log && cd .. && \
    rm -rf gnutls-3.4.10*

# Install tools for mbedtls/tests/scripts/all.sh
RUN apt-get update > /dev/null && \
    apt-get install -y doxygen && \
    apt-get clean && rm -rf /var/lib/apt/lists/

# Remove Ubuntu unattended-upgrades to prevent unwanted changes to system whlei it is running
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
ARG AGENT_WORKDIR=/var/lib/ws
RUN mkdir -p ${AGENT_WORKDIR} && chown user:user ${AGENT_WORKDIR}

USER user
ENV AGENT_WORKDIR=${AGENT_WORKDIR}
ENV PATH=$PATH:/opt/gcc-arm-none-eabi-5_4-2016q3/bin:/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin

WORKDIR ${AGENT_WORKDIR}

# Start interactive shell on run.
ENTRYPOINT ["bash"]

