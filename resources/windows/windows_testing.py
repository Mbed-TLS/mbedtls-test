#!/usr/bin/env python3

#  Copyright (c) 2019-2021, Arm Limited, All Rights Reserved
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

"""
The script checks that Mbed TLS can be built on Windows in different
configurations of Visual Studio, as well as MinGW32. Returns 0 on success,
1 on test failure, and 2 if there is an error while running the script.
Requires prettytable package.
"""

from collections import OrderedDict
from prettytable import PrettyTable

import argparse
import json
import logging
import os
import re
import subprocess
import tempfile
import shutil
import sys
import traceback
import glob


class VStestrun(object):

    def __init__(self, vs_version, configuration, architecture, retargeted):
        self.vs_version = vs_version
        self.configuration = configuration
        self.architecture = architecture
        self.retargeted = retargeted
        self.run_failed = False
        self.results = OrderedDict([
            ("shipped build", "Not Run"),
            ("cmake build", "Not Run"),
            ("cmake test suites", "Not Run"),
        ])


class MbedWindowsTesting(object):
    """For testing the building of mbed TLS on Windows."""

    def __init__(self,
                 repository_path,
                 logging_directory,
                 build_method,
                 testing_config):
        self.repository_path = repository_path
        self.log_dir = logging_directory
        self.return_code = 0
        if "config_to_disable" in testing_config.keys():
            self.config_to_disable = testing_config["config_to_disable"]
        else:
            self.config_to_disable = [
                "MBEDTLS_MEMORY_DEBUG",
                "MBEDTLS_MEMORY_BACKTRACE",
                "MBEDTLS_MEMORY_BUFFER_ALLOC_C",
                "MBEDTLS_THREADING_PTHREAD",
                "MBEDTLS_THREADING_ALT",
                "MBEDTLS_THREADING_C",
                "MBEDTLS_DEPRECATED_WARNING"
            ]
        if "visual_studio_configurations" in testing_config.keys():
            self.visual_studio_configurations = testing_config[
                "visual_studio_configurations"]
        else:
            self.visual_studio_configurations = ["Release", "Debug"]
        if "visual_studio_architectures" in testing_config.keys():
            self.visual_studio_architectures = testing_config[
                "visual_studio_architectures"]
        else:
            self.visual_studio_architectures = ["Win32", "x64"]
        if "visual_studio_versions" in testing_config.keys():
            self.visual_studio_versions = sorted(
                testing_config["visual_studio_versions"].keys()
            )
            self.visual_studio_vcvars_path = testing_config[
                "visual_studio_versions"]
        else:
            self.visual_studio_versions = ["2010", "2013", "2015", "2017"]
            self.visual_studio_vcvars_path = {
                "2010": os.path.join(
                    "C:", os.sep, "Program Files (x86)",
                    "Microsoft Visual Studio 10.0", "VC", "vcvarsall.bat"
                ),
                "2013": os.path.join(
                    "C:", os.sep, "Program Files (x86)",
                    "Microsoft Visual Studio 12.0", "VC", "vcvarsall.bat"
                ),
                "2015": os.path.join(
                    "C:", os.sep, "Program Files (x86)",
                    "Microsoft Visual Studio 14.0", "VC", "vcvarsall.bat"
                ),
                "2017": os.path.join(
                    "C:", os.sep, "Program Files (x86)",
                    "Microsoft Visual Studio", "2017", "Community", "VC",
                    "Auxiliary", "Build", "vcvarsall.bat"
                )
            }
        if "mingw_directory" in testing_config.keys():
            self.mingw_directory = testing_config["mingw_directory"]
        else:
            self.mingw_directory = os.path.join(
                "C:", "tools", "mingw64", "bin"
            )
        if "visual_studio_solution_types" in testing_config.keys():
            self.visual_studio_solution_types = testing_config["visual_studio_solution_types"]
        else:
            self.visual_studio_solution_types = ["shipped", "cmake"]
        if "visual_studio_retarget_solution" in testing_config.keys():
            self.visual_studio_retarget_solution = testing_config["visual_studio_retarget_solution"]
        else:
            self.visual_studio_retarget_solution = [False, True]

        self.vs_version_toolsets = {
            "2010": "100",
            "2013": "120",
            "2015": "140",
            "2017": "141"
        }
        self.visual_studio_architecture_flags = {"Win32": "x86", "x64": "x64"}
        self.cmake_architecture_flags = {"Win32": "", "x64": " Win64"}
        self.cmake_generators = {
            "2010": "Visual Studio 10 2010",
            "2013": "Visual Studio 12 2013",
            "2015": "Visual Studio 14 2015",
            "2017": "Visual Studio 15 2017"
        }
        self.vs_test_runs = []
        self.build_mingw = "mingw" in build_method
        self.vs_versions_to_build = list(
            set(build_method) & set(self.visual_studio_versions)
        )
        # When parsing mingw_result, None indicates that an exception occurred
        # during the test run. This could be either CI or the build failing.
        # True and False indicate success or failure respectively.
        self.mingw_result = None
        self.solution_file_pattern = r"(?i)mbed *TLS\.sln\Z"
        self.visual_studio_build_success_patterns = [
            "Build succeeded.", "\d+ Warning\(s\)", "0 Error\(s\)"
        ]
        self.visual_studio_build_zero_warnings_string = "0 Warning(s)"
        self.test_suites_success_pattern = "100% tests passed, 0 tests failed"
        self.mingw_success_pattern = "PASSED \(\d+ suites, \d+ tests run\)"
        self.config_py_location = os.path.join("scripts", "config.py")
        self.mingw_command = "mingw32-make"
        self.git_command = "git"
        self.python_command = "python"

    def this_version_forbids_c99(self, path):
        # If CMakeLists.txt contains -Wdeclaration-after-statement,
        # this version is intended to build with C89 in Visual Studio 2010.
        # If it doesn't, this version is either from before PolarSSL 0.12
        # or recent enough to be written in C99 which VS2010 doesn't support.
        cmakelists_path = os.path.join(path, 'CMakeLists.txt')
        with open(cmakelists_path) as cmakelists_file:
            content = cmakelists_file.read()
            return '-Wdeclaration-after-statement' in content

    def set_return_code(self, return_code):
        if return_code > self.return_code:
            self.return_code = return_code

    def setup_logger(self, name, log_file, level=logging.INFO):
        """Creates a logger that outputs both to console and to log_file"""
        log_formatter = logging.Formatter(
            "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
        )
        console = logging.StreamHandler()
        file_handler = logging.FileHandler(log_file)
        if name != "Results":
            console.setFormatter(log_formatter)
            file_handler.setFormatter(log_formatter)
        logger = logging.getLogger(name)
        logger.setLevel(level)
        logger.addHandler(file_handler)
        logger.addHandler(console)
        return logger

    def get_clean_worktree_for_git_reference(self, logger):
        logger.info("Checking out git worktree")
        git_worktree_path = os.path.abspath(tempfile.mkdtemp())
        try:
            worktree_output = subprocess.run(
                [self.git_command, "worktree", "add", "--detach",
                 git_worktree_path, "HEAD"],
                cwd=self.repository_path,
                encoding=sys.stdout.encoding,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(worktree_output.stdout)
            submodule_output = subprocess.run(
                [self.git_command, "submodule", "foreach", "--recursive",
                 'git worktree add --detach "{}/$displaypath" HEAD'.format(git_worktree_path)],
                cwd=self.repository_path,
                encoding=sys.stdout.encoding,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(submodule_output.stdout)
            return git_worktree_path
        except subprocess.CalledProcessError as error:
            self.set_return_code(2)
            logger.error(error.output)
            raise Exception("Checking out worktree failed, aborting")

    def cleanup_git_worktree(self, git_worktree_path, logger):
        shutil.rmtree(git_worktree_path)
        try:
            worktree_output = subprocess.run(
                [self.git_command, "worktree", "prune"],
                cwd=self.repository_path,
                encoding=sys.stdout.encoding,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(worktree_output.stdout)
            submodule_output = subprocess.run(
                [self.git_command, "submodule", "foreach", "--recursive", "git worktree prune"],
                cwd=self.repository_path,
                encoding=sys.stdout.encoding,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(submodule_output.stdout)
        except subprocess.CalledProcessError as error:
            self.set_return_code(2)
            logger.error(error.output)
            raise Exception("Worktree cleanup failed, aborting")

    def set_config_on_code(self, git_worktree_path, logger):
        """Enables all config specified in config.pl, then disables config
         based on the version being tested."""
        logger.info("Enabling as much of {} as possible".format(
                self.config_py_location
        ))
        try:
            enable_output = subprocess.run(
                [self.python_command, self.config_py_location, "full"],
                cwd=git_worktree_path,
                encoding=sys.stdout.encoding,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(enable_output.stdout)
            for option in self.config_to_disable:
                disable_output = subprocess.run(
                    [self.python_command, self.config_py_location,
                     "unset", option],
                    cwd=git_worktree_path,
                    encoding=sys.stdout.encoding,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    check=True
                )
                logger.info(disable_output.stdout)
        except subprocess.CalledProcessError as error:
            self.set_return_code(2)
            logger.error(error.output)
            raise Exception("Setting config failed, aborting")

    def generate_seedfile(self, filename):
        """This tests if a file exists, and if not, creates it with 64 bytes
        of random data, which is large enough for all configurations."""
        exists = os.path.isfile(filename)
        if not exists:
            file = open(filename, "wb+")
            file.write(os.urandom(64))
            file.close()

    def generate_seedfiles(self, git_worktree_path):
        """This ensures that seedfiles in both mbedtls and crypto directory
        are present, and if not, creates them."""
        seed_filename = os.path.join(git_worktree_path, "tests/seedfile")
        self.generate_seedfile(seed_filename)
        if os.path.isdir(os.path.join(git_worktree_path, "tf-psa-crypto/tests")):
            crypto_seed_filename = os.path.join(git_worktree_path,
                "tf-psa-crypto/tests/seedfile")
            self.generate_seedfile(crypto_seed_filename)

    def test_mingw_built_code(self):
        """This checks out the git reference in a worktree, sets the necessary
        config and then builds and tests using MinGW. The result is determined
        by parsing the output for any test failures."""
        log_name = "MinGW"
        mingw_logger = self.setup_logger(
            log_name, os.path.join(self.log_dir, log_name + ".txt")
        )
        git_worktree_path = None
        try:
            git_worktree_path = self.get_clean_worktree_for_git_reference(
                mingw_logger
            )
            self.set_config_on_code(git_worktree_path, mingw_logger)
            self.generate_seedfiles(git_worktree_path)
            self.mingw_result = self.build_and_test_using_mingw(
                git_worktree_path, mingw_logger
            )
            if not self.mingw_result:
                self.set_return_code(1)
        except Exception as error:
            self.set_return_code(2)
            mingw_logger.error(error)
            traceback.print_exc()
        finally:
            if git_worktree_path:
                self.cleanup_git_worktree(git_worktree_path, mingw_logger)

    def get_environment_containing_mingw_path(self):
        """This first checks if the mingw command exists on the path,
         if not it searches the system for the command and adds it to the path.
          This is needed for the MinGW tools to work correctly"""
        my_environment = os.environ.copy()
        if not shutil.which(self.mingw_command):
            my_environment["PATH"] = "{};{}".format(
                self.mingw_directory, my_environment["PATH"]
            )
        return my_environment

    def build_and_test_using_mingw(self, git_worktree_path, logger):
        my_environment = self.get_environment_containing_mingw_path()
        my_environment["WINDOWS"] = "1"
        logger.info("Building mbed TLS using {}".format(self.mingw_command))
        try:
            mingw_clean = subprocess.run(
                [self.mingw_command, "clean"],
                env=my_environment,
                encoding=sys.stdout.encoding,
                cwd=git_worktree_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(mingw_clean.stdout)
            mingw_check = subprocess.run(
                [self.mingw_command, "CC=gcc", "check"],
                env=my_environment,
                encoding=sys.stdout.encoding,
                cwd=git_worktree_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(mingw_check.stdout)
            if re.search(self.mingw_success_pattern, mingw_check.stdout):
                return True
            else:
                self.set_return_code(1)
                return False
        except subprocess.CalledProcessError as error:
            self.set_return_code(2)
            logger.error(error.output)
            return False

    def run_test_suites_on_built_code(self, solution_dir, test_run, logger):
        """Runs the various test suites and parses the output to check that
        all the tests pass"""
        my_environment = self.get_environment_containing_VSCMD_START_DIR(
            solution_dir
        )
        my_environment["CTEST_OUTPUT_ON_FAILURE"] = "1"
        msbuild_test_process = subprocess.Popen(
            ["cmd.exe"],
            env=my_environment,
            encoding=sys.stdout.encoding,
            cwd=solution_dir,
            stdin=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            stdout=subprocess.PIPE,
        )
        msbuild_test_process.stdin.write("\"{}\" {}\n".format(
            self.visual_studio_vcvars_path[test_run.vs_version],
            self.visual_studio_architecture_flags[test_run.architecture]
        ))
        msbuild_test_process.stdin.write(
            "msbuild /nodeReuse:false /p:Configuration={} /m RUN_TESTS.vcxproj\n".format(
                test_run.configuration
            )
        )
        msbuild_test_process.stdin.close()
        msbuild_test_output, _ = msbuild_test_process.communicate()
        logger.info(msbuild_test_output)
        if (msbuild_test_process.returncode == 0 and
                all(re.search(x, msbuild_test_output) for x in
                    [self.test_suites_success_pattern] +
                    self.visual_studio_build_success_patterns)):
            return "Pass"
        else:
            self.set_return_code(1)
            return "Fail"

    def get_environment_containing_VSCMD_START_DIR(self, solution_dir):
        """This is done to bypass a 'feature' added in Visual Studio 2017.
         If the %USERPROFILE%\Source directory exists, then running
         vcvarsall.bat will silently change the directory to that directory.
         Setting the VSCMD_START_DIR environment variable causes it to change
         to that directory instead"""
        my_environment = os.environ.copy()
        my_environment["VSCMD_START_DIR"] = solution_dir
        return my_environment

    def build_code_using_visual_studio(self,
                                       solution_dir,
                                       test_run,
                                       solution_type,
                                       logger,
                                       c89):
        logger.info("Building mbed TLS using Visual Studio v{}".format(
            test_run.vs_version
        ))
        my_environment = self.get_environment_containing_VSCMD_START_DIR(
            solution_dir
        )
        if test_run.retargeted:
            retarget = "v{}".format(
                self.vs_version_toolsets[test_run.vs_version]
            )
        elif c89:
            retarget = "Windows7.1SDK"  # Workaround for missing 2010 x64 tools
        else:
            retarget = "v120" # Visual Studio 2013
        logger.info("retarget={}".format(retarget))
        for solution_file in os.listdir(solution_dir):
            if re.match(self.solution_file_pattern, solution_file):
                break
        else:
            logger.error("Solution file missing")
            self.set_return_code(1)
            test_run.results[solution_type + " build"] = "Fail"
            return False
        msbuild_process = subprocess.Popen(
            ["cmd.exe"],
            env=my_environment,
            encoding=sys.stdout.encoding,
            cwd=solution_dir,
            stdin=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            stdout=subprocess.PIPE,
        )
        msbuild_process.stdin.write("\"{}\" {}\n".format(
            self.visual_studio_vcvars_path[test_run.vs_version],
            self.visual_studio_architecture_flags[test_run.architecture]
        ))
        msbuild_process.stdin.write(
            "msbuild /nodeReuse:false /t:Rebuild /p:Configuration={},Platform={},"
            "PlatformToolset={} /m \"{}\"\n".format(
                test_run.configuration, test_run.architecture,
                retarget, solution_file
            )
        )
        msbuild_process.stdin.close()
        msbuild_output, _ = msbuild_process.communicate()
        logger.info(msbuild_output)
        if (msbuild_process.returncode == 0 and
                all(re.search(x, msbuild_output) for x in
                    self.visual_studio_build_success_patterns)):
            if self.visual_studio_build_zero_warnings_string in msbuild_output:
                build_result = "Pass"
            else:
                build_result = "Pass with warnings"
            test_run.results[solution_type + " build"] = build_result
            return True
        else:
            self.set_return_code(1)
            test_run.results[solution_type + " build"] = "Fail"
            return False

    def build_visual_studio_solution_using_cmake(self,
                                                 git_worktree_path,
                                                 test_run,
                                                 logger):
        solution_dir = os.path.join(git_worktree_path, "cmake_solution")
        os.makedirs(solution_dir)
        try:
            cmake_output = subprocess.run(
                ["cmake", "-D", "ENABLE_TESTING=ON", "-G",
                 "{}{}".format(
                     self.cmake_generators[test_run.vs_version],
                     self.cmake_architecture_flags[test_run.architecture]),
                 ".."],
                cwd=solution_dir,
                encoding=sys.stdout.encoding,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=True
            )
            logger.info(cmake_output.stdout)
            return solution_dir
        except subprocess.CalledProcessError as error:
            self.set_return_code(2)
            logger.error(error.output)
            raise Exception("Building solution using Cmake failed, aborting")

    def generate_source_files(self, git_worktree_path, logger):
        """Generate configuration-independent source files if required."""
        batch_script = os.path.join("scripts", "make_generated_files.bat")
        if not os.path.exists(os.path.join(git_worktree_path, batch_script)):
            return
        try:
            vcvars_bat = self.visual_studio_vcvars_path['2017']
            cmd = 'call "{}" x64 && cd /D "%VSCMD_START_DIR%" && "{}"'.format(
                vcvars_bat, batch_script
            )
            env = self.get_environment_containing_VSCMD_START_DIR(
                git_worktree_path
            )
            env['CC'] = 'cl.exe'
            logger.info('Generating source files: ' + cmd)
            subprocess.run(
                cmd, shell=True,
                cwd=git_worktree_path,
                env=env,
                encoding=sys.stdout.encoding,
                check=True
            )
        except subprocess.CalledProcessError as error:
            self.set_return_code(2)
            logger.error(error.output)
            raise Exception("{} failed, aborting".format(batch_script))

    def test_visual_studio_built_code(self, test_run, solution_type):
        log_name = "VS{} {} {}{} {}".format(
            test_run.vs_version,
            test_run.configuration,
            test_run.architecture,
            " Retargeted" if test_run.retargeted else "",
            solution_type
        )
        vs_logger = self.setup_logger(
            log_name, os.path.join(self.log_dir, log_name + ".txt")
        )
        git_worktree_path = None
        try:
            git_worktree_path = self.get_clean_worktree_for_git_reference(
                vs_logger
            )
            c89 = self.this_version_forbids_c99(git_worktree_path)
            if test_run.vs_version == '2010' and not c89:
                for key in test_run.results:
                    test_run.results[key] = 'Skipped'
                return
            self.set_config_on_code(git_worktree_path, vs_logger)
            self.generate_seedfiles(git_worktree_path)
            self.generate_source_files(git_worktree_path, vs_logger)
            if solution_type == "cmake":
                solution_dir = self.build_visual_studio_solution_using_cmake(
                    git_worktree_path, test_run, vs_logger
                )
            else:
                solution_dirs = glob.glob(os.path.join(
                        git_worktree_path, "visualc", "VS*"))
                if len(solution_dirs) != 1:
                    raise Exception(
                        "Found {} paths matching visualc/VS*, expected exactly one".format(len(solution_dirs))
                    )
                solution_dir = solution_dirs[0]
                if not os.path.isdir(solution_dir):
                    raise Exception(
                        "Found file instead of directory when looking "
                        "for VS solution directory: {}".format(solution_dir)
                    )
            build_result = self.build_code_using_visual_studio(
                solution_dir, test_run, solution_type, vs_logger, c89
            )
            if build_result:
                if solution_type == "cmake":
                    test_suites_result = self.run_test_suites_on_built_code(
                        solution_dir, test_run, vs_logger
                    )
                    test_run.results["cmake test suites"] = test_suites_result
        except Exception as error:
            vs_logger.error(error)
            traceback.print_exc()
            self.set_return_code(2)
            test_run.run_failed = True
        finally:
            if git_worktree_path:
                self.cleanup_git_worktree(git_worktree_path, vs_logger)

    def log_results(self):
        result_logger = self.setup_logger(
            "Results",
            os.path.join(self.log_dir, "results.txt")
        )
        total_test_runs = 0
        successful_test_runs = 0
        if self.build_mingw:
            if self.mingw_result is not None:
                total_test_runs += 1
                if self.mingw_result:
                    successful_test_runs += 1
                result_logger.info("MingW build {}".format(
                    "passed" if self.mingw_result else "failed"
                ))
            else:
                result_logger.info(
                    "An error occurred while testing MinGW build"
                )
        if self.vs_versions_to_build:
            total_test_runs += len(self.vs_test_runs)
            result_table = PrettyTable([
                "Version",
                "Configuration",
                "Architecture",
                "Retargeted",
                "shipped build",
                "cmake build",
                "cmake test suites"
            ])
            result_table.align["version"] = "l"
            for test_run in self.vs_test_runs:
                if all("Pass" in result for result
                       in test_run.results.values()):
                    successful_test_runs += 1
                result_table.add_row([
                    test_run.vs_version,
                    test_run.configuration,
                    test_run.architecture,
                    test_run.retargeted,
                    test_run.results["shipped build"],
                    test_run.results["cmake build"],
                    test_run.results["cmake test suites"],
                ])
            result_logger.info(result_table)
        result_logger.info(
            "{} configurations tested, {} successful".format(
                total_test_runs, successful_test_runs
            )
        )

    def run_all_tests(self):
        try:
            if self.build_mingw:
                self.test_mingw_built_code()
            if self.vs_versions_to_build:
                self.vs_test_runs = [
                    VStestrun(vs_version, configuration,
                              architecture, retargeted) for
                    vs_version in self.vs_versions_to_build for
                    configuration in self.visual_studio_configurations for
                    architecture in self.visual_studio_architectures for
                    retargeted in self.visual_studio_retarget_solution if
                    ((vs_version, architecture) != ("2010", "x64") and
                     (vs_version, retargeted) != ("2010", True))
                ]
                for vs_test_run in self.vs_test_runs:
                    for solution_type in self.visual_studio_solution_types:
                        self.test_visual_studio_built_code(
                            vs_test_run, solution_type
                        )
        except Exception:
            traceback.print_exc()
            self.set_return_code(2)
        finally:
            self.log_results()
            sys.exit(self.return_code)


def run_main():
    parser = argparse.ArgumentParser(
        description=(
            """The script checks that Mbed TLS can be built on Windows in
            different configurations of Visual Studio, as well as MinGW32.
            Returns 0 on success, 1 on test failure, and 2 if there is an
            error while running the script. Requires prettytable package."""
        )
    )
    parser.add_argument(
        "repo_path", type=str, help="the path to the Mbed TLS repository"
    )
    parser.add_argument(
        "log_path", type=str, help="the directory path for log files"
    )
    parser.add_argument(
        "-b", "--build-method", type=str, nargs="+",
        choices=["mingw", "2010", "2013", "2015", "2017"],
        default=["mingw", "2010", "2013", "2015", "2017"],
        help="which build methods to test"
    )
    parser.add_argument(
        "-c", "--configuration-file", type=str,
        help="optional path to a json file for non-default testing"
    )

    windows_testing_args = parser.parse_args()
    if windows_testing_args.configuration_file is not None:
        with open(windows_testing_args.configuration_file) as f:
            testing_config = json.load(f)
    else:
        testing_config = {}
    mbed_test = MbedWindowsTesting(
        windows_testing_args.repo_path,
        windows_testing_args.log_path,
        windows_testing_args.build_method,
        testing_config
    )
    mbed_test.run_all_tests()

if __name__ == "__main__":
    run_main()
