# mbed TLS Release Test Report

## Abstract

This is the test report for the mbed TLS Releases

## Introduction

This document presents the details of testing and test results of the mbed TLS library Release.

mbed TLS is a small footprint, open source library which implements the TLS/SSL protocol as well as the many necessary cryptographic primitives used in the TLS protocol. It is suitable for use in embedded systems as well as servers, and is widely deployed. As a library it can be statically linked into application code for embedded use, or dynamically linked for deployment into PCs and servers and is included in many Linux distributions.

### Objectives

The aims of these tests are to ensure the high level of security and quality of mbed TLS regardless of

* how it is shipped to customers or partners
* in what environment it is built
* what target it is built for

## Test plan

The following sections describe the different types of tests executed on the library.

### Types of testing

We automatically check:

* Compilation on different systems
* Code coverage
* Regressions, test vectors, corner cases
* Interoperability with other SSL libraries
* Different build configurations
* Memory leaks
* Memory integrity (bounds, initialization)

The CI system runs all these tests on a number of different build environments automatically.

### Test framework

There are currently over 8800 automated test vectors that are run in the tests/ directory when make check is run.

For running these tests we have our own test framework that combines 'generic' test functions with specific test values. The build system generates the parsing test applications, such as test_suite_aes.ecb and then runs them with the different test case input available. The system keeps in mind the current configuration from config.h and makes sure only relevant tests are run.

### Interoperability testing

Because our test vectors can only test individual functions, we also run interoperability tests to check live SSL connections against OpenSSL and GnuTLS.

The test script tests/compat.sh checks each available ciphersuite in the current build as a server and a client against mbed TLS, OpenSSL and GnuTLS (when available). These tests are run for each protocol version, with and without client authentication. In total over 1600 combinations are tested.

And depending on the settings, memory leaks are checked for automatically as well.

### Security parameter testing

In addition to standard interoperability with other libraries, different behaviors that should occur during a handshake or afterwards are tested for. For this we use the script tests/ssl-opt.sh.

### Different build configurations

The script tests/scripts/test-ref-configs.pl builds and checks different build configurations.

The current configurations tested are:

* A minimal TLS 1.1 configuration (configs/config-mini-tls1_1.h)
* A Suite B compatible configuration (configs/config-suite-b.h)
* A minimal "modern" PSK configuration using TLS 1.2 and AES-CCM-8 (configs/config-ccm-psk-tls1_2.h)

### Memory checks

Both automatic and manual tests are run with the Valgrind memory checker and with various sanitizers from GCC and Clang: ASan for memory bounds checking, MemSan to detect uninitialised memory issues, and UBSan to detect undefined behaviors according to the C standard.

### Test suites

The following tests are performed in all.sh:

* Check for recursion
* Check if the generated files are fresh
* Check doxygen markup and warnings
* Check declared and exported names
* Build a yotta module
* ASan build of the default configuration with unit, interoperability and option testing
* ASan build of the four reference configurations with unit tests, interoperability and option testing
* ASan build of SSLv3 enabled configuration with unit tests, interoperability and option testing
* Build of the full configuration with unit tests, limited interoperability and limited option testing
* Etc.

### Test systems

At the moment we test on the following Operating Systems:

* Ubuntu Intel 64-bit (Travis)
* Debian with Intel 32-bit, Intel 64-bit, and ARM 32-bit
* FreeBSD i386
* Win32 32-bit and 64-bit

With a mix of the following compilers / IDE environments:

* GCC (with CMake and make)
* Clang
* MinGW32
* MS Visual Studio 12
* armcc

Also we test on target in the following environments

* K64F with yotta
* K64F with Morpheus

### Static analysis

The mbed TLS source code is automatically checked with the Coverity static analysis scanner for security issues before every new release. You can find more details on the mbed TLS page on Coverity.

In addition, we manually use Clang's static analyzer and more recently Infer on our codebase.

### Fuzzing

We use Codenomicon Defensics for fuzzing of our (D)TLS server (all versions) and our X.509 code before every new release.This step was omitted in the case of the current release

### Test configuration

We used the following versions of software for interoperability testing:
* OpenSSL 1.0.1j 15 Oct 2014
* gnutls-serv 3.3.11
* gnutls-cli 3.3.11
