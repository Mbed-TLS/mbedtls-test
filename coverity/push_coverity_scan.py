#!/usr/bin/env python3

""" Build Mbed TLS using the coverity toolset and upload said build to coverity.

A small script designed to be run both in the CI and on local users machines.

Required:

1. Path to Mbed TLS directory
2. A project coverity token (got from the Coverity site / Project Settings
3. An email address to send notifications to.

(The last of these is annoying, but things will not work without it, all that it
is used for is to notify that the build was submitted)

The token can either be passed in as an argument with -t, or via the environment
in the variable 'COVERITY_TOKEN'

Other options:

* -b / --branch: If specified, this branch will be checked out prior to build.
* -c / --covtools: If specified, the coverity tools will be downloaded here. If
    there is already a set of coverity tools in the specified directory, they
    will be checked for appropriate version, and overwritten only if necessary.
* -e / --email: Email address to send build notifications to
* -t / --token: The Coverity Scan token - needs to be got from the coverity web
    UI
* -l / --log: Specify a file to log information on the build to.
* -m / --backupdir: If specified, this will be used as a directory to backup
    built tar files to.
* -n / --no-upload: If set, build the Coverity tar file, but do not request a
    build or upload it.
* -o / --os: If specified, override the OS specification for the coverity tools
    default is 64 bit linux
* -p / --pre-build-step: Specify the command to run pre - build.
    defaults to 'make clean'.
* -s / --build-step: Specify the command to run to build the project.
    defaults to 'make -j <num_cpus>'.
* -v / --verbose: If specified, all logging will be done to stdout.

"""

# Copyright The Mbed TLS Contributors
# SPDX-License-Identifier: Apache-2.0 OR GPL-2.0-or-later

import argparse
from subprocess import run, CalledProcessError
import shlex
from traceback import format_exc

from typing import Iterable

import os
import pathlib
import shutil
from tempfile import mkdtemp, NamedTemporaryFile
from datetime import datetime
import tarfile
import hashlib
import logging
import sys
from multiprocessing import cpu_count
from io import BytesIO

import requests

class ConfigError(Exception):
    """ Exception class for configuration errors """
    pass

def check_coverity_scan_tools_version(token: str, tools_os: str, tools_dir: str) -> bool:
    """ Get the md5 of the coverity tools package from coverity.

    Enable us to check that we have the latest version, saving a potential large download.
    """

    query_data = [('token', token),
                  ('project', 'ARMmbed/mbedtls'),
                  ('md5', '1')]

    md5_request = requests.get('https://scan.coverity.com/download/' + tools_os, data=query_data,
                               timeout=60)
    md5_request.raise_for_status()

    md5_path = pathlib.Path(tools_dir)
    md5_path = md5_path / 'coverity_tool.md5'

    with md5_path.open('r') as md5_file:
        tools_hash = md5_file.read()

    return tools_hash == md5_request.text

def backup_config_file(logger: logging.Logger, config_path: pathlib.Path, restore: bool) -> None:
    """Backup / Restore a single config file. """

    config_path.resolve()

    backup_path = config_path.with_suffix('.h.bak')

    if restore:
        if backup_path.is_file():
            logger.log(logging.INFO, "replacing {} with {}".format(config_path, backup_path))
            backup_path.replace(config_path)
        else:
            logger.log(logging.INFO, "backup {} does not exist".format(backup_path))
    else:
        logger.log(logging.INFO, "backing up {} to {}".format(config_path, backup_path))
        shutil.copy(config_path, config_path.with_suffix('.h.bak'))


def backup_config_files(logger: logging.Logger, mbedtls_dir: pathlib.Path, restore: bool) -> None:
    """Backup / Restore all config files that we will change. """

    config_path = pathlib.Path(mbedtls_dir)
    config_path = config_path / 'include' / 'mbedtls' / 'mbedtls_config.h'

    backup_config_file(logger, config_path, restore)

    config_path = pathlib.Path(mbedtls_dir)
    config_path = config_path / 'tf-psa-crypto' / 'include' / 'psa' / 'crypto_config.h'

    backup_config_file(logger, config_path, restore)

def filter_root_tar_dir(tar_file: tarfile.TarFile) -> Iterable[tarfile.TarInfo]:

    """ Filter the tar file root dir out.

    This function allows extraction of the contents of the first containing directory in the tar
    file directly to the target folder, by stripping the first containing folder from each path.
    """

    for tar_member in tar_file.getmembers():

        member_path = pathlib.Path(tar_member.path)

        if len(member_path.parts) > 1:
            tar_member.path = str(member_path.relative_to(*member_path.parts[:1]))
            yield tar_member

def download_coverity_scan_tools(logger: logging.Logger, token: str, tools_os: str, tools_dir: str) -> None:

    """ Download the coverity scan tools.

    Download the required coverity scan tools to the given directory, using the passed in token.
    """

    query_data = [('token', token),
                  ('project', 'ARMmbed/mbedtls')]

    logger.log(logging.INFO, "Downloading Coverity Scan....")

    package_request = requests.get('https://scan.coverity.com/download/' + tools_os, data=query_data,
                                   timeout=60)
    package_request.raise_for_status()

    tools_hash = hashlib.md5(package_request.content).hexdigest()

    # Extract the (filtered) downloaded tar file to the target dir.
    with tarfile.open(fileobj=BytesIO(package_request.content)) as tar_file:
        tar_file.extractall(path=tools_dir, members=filter_root_tar_dir(tar_file), filter='data')

    md5_path = pathlib.Path(tools_dir)
    md5_path = md5_path / 'coverity_tool.md5'

    with md5_path.open('w') as md5_file:
        md5_file.write(tools_hash)

def build_mbedtls(logger: logging.Logger, mbedtls_dir: pathlib.Path, tools_dir: pathlib.Path,
                  branch: str, pre_build_step: str, build_step: str, tar_file_name: str) -> None:
    """ Build Mbed TLS, using the coverity tools.

    Build the MBed TLS source located in the passed in dir, using the tools specified, using the
    given pre-build and build commands. Tar the results up into the given file name, as required by
    coverity.
    """

    os.chdir(mbedtls_dir)

    # Ensure that given git directory is up to date.
    result = run(['git', 'fetch', '--all'], capture_output=True, check=True)
    logger.log(logging.INFO, result.stdout.decode("utf-8"))

    # Switch to correct branch.
    if branch is not None:
        result = run(['git', 'checkout', '--recurse-submodules', branch], capture_output=True,
                     check=True)
        logger.log(logging.INFO, result.stdout.decode("utf-8"))

        result = run(['git', 'submodule', 'update', '--init', '--recursive'], capture_output=True,
                      check=True)
        logger.log(logging.INFO, result.stdout.decode("utf-8"))


    # Backup config files here prior to running config.py, as the branch checkout may also have
    # changed them, and backing up before this point will end up with old versions of the file being
    # restored.
    backup_config_files(logger, mbedtls_dir, False)

    # Ensure correct library build configuration.
    result = run(['scripts/config.py', 'full_no_platform'], capture_output=True, check=True)
    logger.log(logging.INFO, result.stdout.decode("utf-8"))


    # Do pre-build steps.
    result = run(pre_build_step, capture_output=True, check=True, shell=True)
    logger.log(logging.INFO, result.stdout.decode("utf-8"))

    # Build.
    coverity_tool = tools_dir / 'bin' / 'cov-build'
    result = run([str(coverity_tool), '--dir', 'cov-int'] + shlex.split(build_step),
                 capture_output=True,
                 check=True)
    logger.log(logging.INFO, result.stdout.decode("utf-8"))

    # TODO, ensure enough units were compiled..

    # Tar up the results...
    cov_int_dir = mbedtls_dir / 'cov-int' / ''

    logger.log(logging.INFO, 'Writing {} to tar file : {}'.format(cov_int_dir, tar_file_name))

    with tarfile.open(tar_file_name, "w:gz") as tar_file:
        tar_file.add(str(cov_int_dir), recursive=True, arcname='cov-int')
        tar_file.close()

def upload_build(logger: logging.Logger, token: str, email_address: str, tar_file_name: str) -> None:

    """ Upload build to coverity.

    Upload the build (tar file specified) to the url given by the project, using the passed in auth
    token.
    """

    base_url = 'https://scan.coverity.com/projects/4583/builds/'

    # Step 1. Initialise a build, get an upload url and build ID
    logger.log(logging.INFO, 'Requesting/initialising coverity build')

    # TODO - Allow passing in version, description?
    tar_file_path = pathlib.Path(tar_file_name)

    build_post_data = [('version', ''),
                       ('description', ''),
                       ('email', email_address),
                       ('token', token),
                       ('file_name', tar_file_path.name)]

    build_request = requests.post(base_url + 'init', data=build_post_data, timeout=60)
    build_request.raise_for_status()

    build_response = build_request.json()

    logger.log(logging.INFO, 'Got coverity build ID {}'.format(build_response['build_id']))

    # Step 2. Upload the previously created tar file to the upload url received in the last step.
    logger.log(logging.INFO, 'Uploading tar file to {}'.format(build_response['url']))

    with open(tar_file_name, 'rb') as data_file:
        upload_headers = {'Content-type': 'application/json'}
        upload_request = requests.put(build_response['url'], data=data_file,
                                      headers=upload_headers, timeout=60)
        upload_request.raise_for_status()

    # Step 4. Trigger the build analysis on coverity (important, if this doesn't happen the build
    # will be 'stuck' - it can be cancelled via the website, where it will be seen as 'in queue' but
    # won't move forwards).

    logger.log(logging.INFO, 'Triggering coverity build')

    trigger_query_data = [('token', token)]
    trigger_url = '{}/{}/enqueue'.format(base_url, build_response['build_id'])
    trigger_request = requests.put(trigger_url, data=trigger_query_data, timeout=60)

    trigger_request.raise_for_status()

def main() -> int:

    parser = argparse.ArgumentParser(description='Push Mbed TLS build to Coverity Scan')
    parser.add_argument('-b', '--branch', help='Branch to check out in mbedtls project')
    parser.add_argument('-c', '--covtools',
                        help='Directory to store downloaded coverity tools in')
    parser.add_argument('-e', '--email', help='Email address to send build notifications to',
                        required=True)
    parser.add_argument('-s', '--build-step', help='Command to run to build the project',
                        default = 'make -j{}'.format(cpu_count()))
    parser.add_argument('-t', '--token', help='Coverity Scan Token')
    parser.add_argument('-l', '--log', help='File to log to')
    parser.add_argument('-m', '--backupdir', help='Directory to backup tar files to')
    parser.add_argument('-n', '--no-upload',
                        help='Build the Coverity tar file, but do not request a build or upload it',
                        action = 'store_true')
    parser.add_argument('-o', '--os', help='Specify OS for coverity tools',
                        choices=['linux64', 'linux-ARM64', 'freebsd64', 'win64'],
                        default='linux64')
    parser.add_argument('-p', '--pre-build-step', help='Command to run pre-build',
                        default='make clean && tests/scripts/check-generated-files.sh -u')
    parser.add_argument('-v', '--verbose', action='store_true',
                        help='Verbose logging to stdout')
    parser.add_argument('mbedtlsdir', help='Mbed TLS directory')

    args = parser.parse_args()

    logger = logging.getLogger("Push_Coverity")
    logger.setLevel(logging.DEBUG)

    stdout_log_formatter = logging.Formatter("%(levelname)s: %(message)s")
    stdout_log_handler = logging.StreamHandler(sys.stdout)

    if args.verbose:
        stdout_log_handler.setLevel(logging.DEBUG)
    else:
        stdout_log_handler.setLevel(logging.ERROR)

    stdout_log_handler.setFormatter(stdout_log_formatter)

    logger.addHandler(stdout_log_handler)

    if args.log is not None:
        file_log_formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s",
                                               datefmt="%H:%M:%S")
        file_log_handler = logging.FileHandler(args.log)
        file_log_handler.setLevel(logging.DEBUG)
        file_log_handler.setFormatter(file_log_formatter)

        logger.addHandler(file_log_handler)

    ret_code = 0

    logger.log(logging.INFO, "### Script starting.")

    tools_path_set = False

    try:
        tools_path = pathlib.Path('')

        mbedtls_path = pathlib.Path(args.mbedtlsdir)
        mbedtls_path = mbedtls_path.resolve()

        token_found = False
        if 'COVERITY_TOKEN' in os.environ:
            coverity_token = os.environ['COVERITY_TOKEN']
            token_found = True

        # Allow passed argument token to override
        if args.token is not None:
            coverity_token = args.token
            token_found = True

        if not token_found:
            raise ConfigError('Coverity token not found')

        if args.covtools is None:
            # If no cov tools dir specified, then use a temporary (long path,
            # given the need to redownload each time).
            dir_path = mkdtemp()
            tools_path = pathlib.Path(dir_path)
            tools_path_set = True

            download_coverity_scan_tools(logger, coverity_token, args.os, tools_path)
        else:
            # Coverity tools dir specified, see if it exists, contains tools and
            # those tools are up to date.
            tools_path = pathlib.Path(args.covtools)
            tools_path = tools_path.resolve()
            tools_path_set = True

            if not tools_path.is_dir():

                logger.log(logging.INFO, 'Tools dir does not exist, creating.')
                tools_path.mkdir()

                download_coverity_scan_tools(logger, coverity_token, args.os, tools_path)
            else:
                hash_path = tools_path / 'coverity_tool.md5'

                if not hash_path.is_file():
                    logger.log(logging.INFO, 'Hash file does not exist, re-downloading.')
                    download_coverity_scan_tools(logger, coverity_token, args.os, tools_path)
                else:
                    # Attempt to check if our coverity scan package is up to date.
                    if not check_coverity_scan_tools_version(coverity_token, args.os, tools_path):
                        logger.log(logging.INFO, 'Hash file differs, re-downloading tools.')
                        download_coverity_scan_tools(logger, coverity_token, args.os, tools_path)

        with NamedTemporaryFile() as tar_file_handle:

            tar_file = pathlib.Path(tar_file_handle.name)

            if not mbedtls_path.is_dir():
                raise ConfigError('MBed TLS directory specified does not exist.')

            build_mbedtls(logger, mbedtls_path, tools_path, args.branch,
                          args.pre_build_step, args.build_step, tar_file)

            # Set this if you want to test without consuming the Coverity credit. The tar file will
            # still obviously be built, and if you use a backup directory to save it, can still be
            # manually uploaded via the website afterwards.
            if not args.no_upload:
                # send completed tar file to coverity
                upload_build(logger, coverity_token, args.email, tar_file)

            # If we want a backup of the tar file, then make one.
            if args.backupdir is not None:
                backup_path = pathlib.Path(args.backupdir)

                if not backup_path.is_dir():
                    logger.log(logging.INFO, 'Backup dir does not exist, creating.')
                    backup_path.mkdir()

                backup_path = backup_path.resolve()
                backup_path = backup_path / datetime.today().strftime('mbedtls-%y-%m-%d.tar.gz')
                shutil.copy(tar_file, backup_path)


    except requests.exceptions.RequestException as e:
        logger.log(logging.ERROR, format_exc())
        ret_code = 1
    except CalledProcessError as e:
        logger.log(logging.ERROR,
                   'Command {} returned {}\n StdOut : {}\n StdErr : {}'.format(e.cmd,
                                                                                e.returncode,
                                                                                e.stdout.decode("utf-8"),
                                                                                e.stderr.decode("utf-8")))
        ret_code = 1
    except:
        logger.log(logging.ERROR, 'Exception occurred: {}'.format(format_exc()))
        ret_code = 1

    finally:
        # Clean up, if necessary
        if args.covtools is None and tools_path_set:
            shutil.rmtree(tools_path)

        backup_config_files(logger, mbedtls_path, True)

    logger.log(logging.INFO, "### Script done.")
    return ret_code


if __name__ == "__main__":
    sys.exit(main())
