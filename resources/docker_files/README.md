# Mbed TLS development Environment on docker

Docker files for some of the supported platforms are provided here. These are prepared with all necessary tools for building and testing Mbed TLS library and it's sample applications (as tested in the CI). These images can also be seen as a reference for building a development enviroment for Mbed TLS.

These images have proved very useful in replicating CI build environment and reproducing build & test failures. Hence, very useful for developers fixing issues found in the CI.

## Development interface

Docker is started with an interactive shell to emulate a development environment. Helper script ```run.sh``` can be used to launch a docker image:
```sh
./run.sh <mount dir> <image tag>
```
```run.sh``` makes it easier to start images with a suitable working environment. It
- mounts a local directory on to the container at startup. Hence, a local checkout of Mbed TLS can be used and artefacts produced can be preserved even after exiting the image.
- mounts ```~/.ssh``` directory to the docker home so that ```git``` can be used from within the docker.
- configures user ids for the docker user to be same as the host user to preserve the permissions on the files created or modified inside the docker.

Remember to build the docker image before running ```run.sh```.

## Docker setup
* **build** - 
A docker image can be built with following command:
```sh
cd mbedtls-test/dev_envs/docker_files
sudo docker build --network=host -t ubuntu-18.04 -f ubuntu-18.04/Dockerfile .
```
This creates an image from the specified file. Built image is maintained by docker in it's own workspace on the host. Don't worry where the built image is gone! From this point the built image is referred by it's tag name. For example ```ubuntu-18.04```.

Note: `--network=host` may or may not necessary depending on your machine's
configuration, including whether you're using a VPN or not.

* **run** -
Following basic command starts docker in an interactive mode:
```sh
sudo docker run --network=host --rm -i -t ubuntu-18.04
```
Above, ```-i``` is for interactive mode and ```-t``` is for emulating a tty. ```--rm``` tells docker to cleanup the container after exit. (See note above regarding `--network=host`.) All images launch ```bash``` on startup. Hence, user is on a ```bash``` shell when image is started in the interactive mode.

Note that the additional parameter `--security-opt seccomp=unconfined` which is disabling the ASLR for the zeroize test, is now set by default.

Use ```run.sh``` for enabling ```git``` and mounting a host workspace inside docker. Example:
```sh
$ ./run.sh /home/mazimkhan/github/mazimkhan ubuntu-18.04
****************************************************
  Running docker image ubuntu-18.04
  User ID:Group ID --> 1000:1000
  Mounting /home/mazimkhan/.ssh --> /home/user/.ssh
  Mounting /home/mazimkhan/github/mazimkhan --> /var/lib/ws
****************************************************
ls
charontls
mbed-os
mbed-os-example-tls
mbed-os-example-tls_armmbed
mbedtls
mbedtls-restricted
mbedtls-test
nRF5_SDK_14.0.0_3bcc1f7
nrf52_dk_example
systest-jenkins-dockerfiles

exit
```

* **listing images**
Built images on host machine can be viewed using command:
```sh
$ sudo docker images
[sudo] password for mazimkhan: 
REPOSITORY             TAG                 IMAGE ID            CREATED             SIZE
ubuntu-18.04           latest              d62631abe664        2 hours ago         2.39GB
ubuntu-16.04           latest              f25e332cb5d8        5 weeks ago         4.04GB
ubuntu                 16.04               747cb2d60bbe        7 weeks ago         122MB
```
