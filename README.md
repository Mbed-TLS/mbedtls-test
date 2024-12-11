# Mbed TLS CI

Out of source test infrastructure information for Mbed TLS.

When raising a PR in [mbedtls](https://github.com/ARMmbed/mbedtls) a range of tests will be run automatically. This repository contains all the information required to reproduce these tests. This can be particularly useful for reproducing failures on a PR.

The docker files in `resources/docker_files` are the ones used by the CI. For more information see the corresponding readme: `resources/docker_files/README.md`.

## Quick Start

To get the docker image used in the CI, run the following command from the root of a fresh checkout of the `master` branch of this repository:
```sh
docker pull trustedfirmware/ci-amd64-mbed-tls-ubuntu:ubuntu-16.04-$(git hash-object resources/docker_files/ubuntu-16.04/Dockerfile)-amd64
```
Then to run the image:
```sh
./resources/docker_files/run.sh <mount dir> trustedfirmware/ci-amd64-mbed-tls-ubuntu:ubuntu-16.04-$(git hash-object resources/docker_files/ubuntu-16.04/Dockerfile)-amd64
```
Where `<mount dir>` is a directory from the host that will be mounted on the container at startup (usually a local checkout of Mbed TLS).

Assuming `<mount dir>` is the root of an Mbed TLS source tree, first install the requirements:
```sh
./scripts/min_requirements.py --user
```
(This will install packages in the `.local` subdirectory of `<mount dir>`.)
Don't worry about the warnings about `.local/bin` not being on `PATH`, our
tests will not rely on the executables but instead use `python -m xxx`.

Then the tests can be run with:
```sh
./tests/scripts/all.sh
```
Note that this runs all the tests that can run in that image. Running a full test campaign requires some tests to run on different images because they require different versions of tools.

For more details on the docker images, see [their dedicated Readme](resources/docker_files/README.md).

## Contribution

This repository accepts contributions only from Mbed TLS maintainers.

## License

The software is provided under the [Apache 2.0 license](LICENSE) (except for some files which specify a different license).
