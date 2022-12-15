# Mbed TLS development Environment on docker

Docker files for some of the supported platforms are provided here. These are prepared with all necessary tools for building and testing Mbed TLS library and it's sample applications (as tested in the CI). These images can also be seen as a reference for building a development enviroment for Mbed TLS.

These images have proved very useful in replicating CI build environment and reproducing build & test failures. Hence, very useful for developers fixing issues found in the CI.

## Using the images


Remember to build the docker image before running ```run.sh```.

## Getting pre-built images from the CI

See the [Quick Start section of the top-level README](../../README.md#quick-start) in this repository.

## Re-builing images locally

A docker image can be built with following command:
```sh
cd mbedtls-test/dev_envs/docker_files
sudo docker build --network=host -t ubuntu-18.04 -f ubuntu-18.04/Dockerfile .
```
This creates an image from the specified file. The built image is maintained by docker in it's own workspace on the host. Don't worry where the built image is gone! From this point the built image is referred by it's tag name. For example ```ubuntu-18.04```. See [Listing images](#listing-images) below.

Note: `--network=host` may or may not necessary depending on your machine's
configuration, including whether you're using a VPN or not.

## Running the images using the helper script

The helper script ```run.sh``` can be used to launch a docker image:
```sh
./run.sh <mount dir> <image tag>
```
```run.sh``` makes it easier to start images with a suitable working environment. It
- mounts a local directory on to the container at startup. Hence, a local checkout of Mbed TLS can be used and artefacts produced can be preserved even after exiting the image.
- mounts ```~/.ssh``` directory to the docker home so that ```git``` can be used from within the docker.
- configures user ids for the docker user to be same as the host user to preserve the permissions on the files created or modified inside the docker.

## Running the images manually

The following basic command starts docker in an interactive mode:
```sh
sudo docker run --network=host --rm -i -t ubuntu-18.04
```
Above, ```-i``` is for interactive mode and ```-t``` is for emulating a tty. ```--rm``` tells docker to cleanup the container after exit. (See note above regarding `--network=host`.) All images launch ```bash``` on startup. Hence, user is on a ```bash``` shell when image is started in the interactive mode.

## Listing images

Built images on host machine can be viewed using command:
```sh
sudo docker images
```
