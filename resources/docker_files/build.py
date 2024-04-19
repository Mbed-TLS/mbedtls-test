#!/usr/bin/env python3

import argparse
import os
import pathlib
import shutil
import subprocess
import sys
import tarfile
import traceback
from typing import IO, BinaryIO, Sequence, Optional, Dict


class BuildException(Exception):
    def __init__(self, message: Optional[str] = None, code: int = 1, print_usage: bool = False) -> None:
        self.message = message
        self.code = code
        self.print_usage = print_usage


class BuildArgumentParser(argparse.ArgumentParser):
    def exit(self, status: int = 0, message: Optional[str] = None) -> None:
        raise BuildException(message, status)

    def error(self, message: str) -> None:
        raise BuildException(message, 2, True)


def build(dockerfiles: Sequence[str], armc5: str = None, armc6: str = None) -> None:
    docker = shutil.which('docker')
    if docker is None:
        raise BuildException("'docker' executable not found in PATH", 128)
    cmdline = [docker, 'buildx', 'build', '--network=host']
    env = dict(os.environ)
    extra_files = {'armc5': armc5, 'armc6': armc6}
    for name in extra_files:
        secret = name.upper() + '_URL'
        env[secret] = 'file:///run/context/{}.tar.gz'.format(name)
        cmdline.append('--secret=type=env,id=' + secret)

    for dockerfile in dockerfiles:
        path = pathlib.Path(dockerfile)
        if path.is_dir():
            path = path / 'Dockerfile'
        print(cmdline)
        with subprocess.Popen(cmdline + ['-'], env=env, stdin=subprocess.PIPE) as proc:
            with tarfile.open(fileobj=proc.stdin, mode='w|', format=tarfile.GNU_FORMAT, dereference=True) as tar:
                tar.add(path, 'Dockerfile')
                for name, file in extra_files.items():
                    name = name + '.tar.gz'
                    if file is None:
                        file = path.parent / name
                        if not file.exists():
                            continue
                    tar.add(file, name)


def main(args: Sequence[str]) -> int:
    parser = BuildArgumentParser(allow_abbrev=False)
    try:
        parser.add_argument('--armc5')
        parser.add_argument('--armc6')
        parser.add_argument('dockerfiles', metavar='Dockerfile', nargs='+')
        build(**vars(parser.parse_args(args)))
    except BuildException as e:
        if e.print_usage:
            parser.print_usage(sys.stderr)
        if e.message:
            print('{}: error:'.format(parser.prog), e.message, file=sys.stderr)
        return e.code
    except Exception:
        traceback.print_exc()
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
