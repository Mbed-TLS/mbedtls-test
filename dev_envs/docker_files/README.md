# Mbed TLS development Environment on docker

Docker files for some of the supported platforms are provided here. These are prepared with all necessary tools for building and testing Mbed TLS library and it's sample applications. These images can also be seen as a reference for building a development enviroment for Mbed TLS.

## Development interface

Docker is started with an interactive shell to emulate a development environment. Helper script ```run.sh``` can be used to launch a docker image:
```sh
./run.sh <mount dir> <image tag>
```
```run.sh``` makes it easier to start images with a suitable working environment. It
- mounts a local directory to the image on startup. Hence, a local checkout of Mbed TLS can be used and artefacts produced can be preserved even after exiting the image. 
- mounts ```~/.ssh``` directory to the docker home so that ```git``` can be used from within the docker. 
- configures user ids for the docker user to be same as the host user to preserve the permissions on the files created or modified inside the docker.

Remember to build the docker image before running ```run.sh```.

## Docker setup
* **build** - 
A docker image can be built with following command:
```sh
cd mbedtls-test/dev_envs/docker_files
sudo docker build -t debian-9-x64 -f debian-9-x64/Dockerfile .
```
This creates an image from the specified file. Built image is maintained by docker in it's own workspace on the host. Don't worry where the built image is gone! After build image is referred by it's tag. For example ```debian-9-x64```.

* **run** -
Following basic command starts docker in an interactive mode:
```sh
sudo docker run --rm -i debian-9-x64
```
Above, ```-i``` is for interactive mode. ```--rm``` tells docker to cleanup the container after exit. All images launch ```bash``` on startup. Hence, user is on a ```bash``` shell when image is started in the interactive mode. **Note** docker does not have a shell prompt that user will notice. Try running ```ls```.

Use ```run.sh``` for enabling ```git``` and mounting a host workspace inside docker. Example:
```sh
$ ./run.sh /home/mazimkhan/github/mazimkhan debian-9-x64
****************************************************
  Running docker image debian-9-x64
  User ID:Group ID --> 1000:1000
  Mounting /home/mazimkhan/.ssh --> /home/user/.ssh
  Mounting /home/mazimkhan/github/mazimkhan --> /var/lib/ws
****************************************************
ls
charontls
findme
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
debian-9-x64           latest              d62631abe664        2 hours ago         2.39GB
ubuntu-16.04-mbedtls   latest              f25e332cb5d8        5 weeks ago         4.04GB
ubuntu                 16.04               747cb2d60bbe        7 weeks ago         122MB
```
