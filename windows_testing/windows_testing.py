#!/usr/bin/env python2

# This script requires python 2.7 and the prettytable package to be installed
# This script is for testing that mbedTLS builds on Windows. The script checks
# that mbedTLS can be built in different configurations of Visual Studio, as
# well as MinGW32

from collections import namedtuple
from prettytable import PrettyTable

import argparse
import json
import logging
import os
import re
import subprocess

VS_test_run = namedtuple(
    "VS_test_run",
    [
        "vs_version",
        "configuration",
        "architecture",
        "should_retarget",
        "mbed_version",
        "solution_type"
    ]
)

VS_result = namedtuple(
    "VS_result",
    [
        "version",
        "configuration",
        "architecture",
        "retargeted",
        "mbed_tag",
        "solution_type",
        "test_type",
        "result"
    ]
)


class MbedWindowsTesting(object):
    """For testing the building of mbedTLS on Windows."""

    def __init__(self,
                 repository_path,
                 logging_directory,
                 git_tag_config,
                 visual_studio_versions,
                 visual_studio_configurations,
                 visual_studio_architectures):
        self.repository_path = repository_path
        self.log_dir = logging_directory
        self.git_tag_config = git_tag_config
        self.visual_studio_configurations = visual_studio_configurations
        self.visual_studio_architectures = visual_studio_architectures
        self.log_formatter = logging.Formatter(
            '%(asctime)s - %(levelname)s - %(message)s'
        )
        self.visual_studio_versions = sorted(visual_studio_versions.keys())
        self.visual_studio_vcvars_path = visual_studio_versions
        self.vs_version_toolsets = {
            "2010": "100",
            "2013": "120",
            "2015": "140",
            "2017": "141"
        }
        self.visual_studio_solution_types = ["shipped", "cmake"]
        self.visual_studio_architecture_flags = {"Win32": "x86", "x64": "x64"}
        self.cmake_architecture_flags = {"Win32": "", "x64": " Win64"}
        self.cmake_generators = {
            "2010": "Visual Studio 10 2010",
            "2013": "Visual Studio 12 2013",
            "2015": "Visual Studio 14 2015",
            "2017": "Visual Studio 15 2017"
        }
        self.visual_studio_results = []
        self.mingw_results = {}
        self.solution_file_pattern = "(?i)mbed *TLS\.sln"
        self.visual_studio_build_success_patterns = [
            "Build succeeded.", "\d+ Warning\(s\)", "0 Error\(s\)"
        ]
        self.visual_studio_build_zero_warnings_string = "0 Warning(s)"
        self.selftest_success_pattern = "\[ All tests (PASS|passed) \]"
        self.test_suites_success_pattern = "100% tests passed, 0 tests failed"
        self.mingw_success_pattern = "PASSED \(\d+ suites, \d+ tests run\)"
        self.config_pl_location = "scripts\\config.pl"
        self.selftest_exe = "selftest.exe"
        self.mingw_command = "mingw32-make"
        self.mingw_directory = None
        self.git_command = "git"
        self.perl_command = "perl"

    def setup_logger(self, name, log_file, level=logging.INFO):
        """Creates a logger that outputs both to console and to log_file"""
        console = logging.StreamHandler()
        console.setLevel(logging.INFO)
        handler = logging.FileHandler(log_file)
        if name is not "results":
            console.setFormatter(self.log_formatter)
            handler.setFormatter(self.log_formatter)
        logger = logging.getLogger(name)
        logger.setLevel(level)
        logger.addHandler(handler)
        logger.addHandler(console)
        return logger

    def set_repository_to_clean_state(self, mbed_tag, logger):
        """Cleans the repository directory and checks out the specified tag"""
        logger.info("Returning repository to clean state")
        git_reset_process = subprocess.Popen(
            [self.git_command, "reset", "--hard", "-q"],
            cwd=self.repository_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )
        git_reset_output, _ = git_reset_process.communicate()
        logger.info(git_reset_output)
        git_clean_process = subprocess.Popen(
            [self.git_command, "clean", "-qdfx"],
            cwd=self.repository_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )
        git_clean_output, _ = git_clean_process.communicate()
        logger.info(git_clean_output)
        logger.info("Checking out code to tag {}".format(mbed_tag))
        git_checkout_process = subprocess.Popen(
            [self.git_command, "checkout", mbed_tag],
            cwd=self.repository_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )
        git_checkout_output, _ = git_checkout_process.communicate()
        logger.info(git_checkout_output)

    def set_config_on_code(self, mbed_version, logger):
        """Enables all config specified in config.pl, then disables config
         based on the version being tested."""
        logger.info("Enabling as much of {} as possible".format(
                self.config_pl_location
        ))
        enable_process = subprocess.Popen(
            [self.perl_command, self.config_pl_location, "full"],
            cwd=self.repository_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )
        enable_output, _ = enable_process.communicate()
        logger.info(enable_output)
        for option in self.git_tag_config[mbed_version]["config_to_disable"]:
            disable_process = subprocess.Popen(
                [self.perl_command, self.config_pl_location, "unset", option],
                cwd=self.repository_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT
            )
            disable_output, _ = disable_process.communicate()
            logger.info(disable_output)

    def test_mingw_built_code(self, mbed_version):
        log_name = "MingW " + self.git_tag_config[mbed_version]["tag"]
        mingw_logger = self.setup_logger(
            log_name, "{}\\{}.txt".format(self.log_dir, log_name)
        )
        try:
            self.set_repository_to_clean_state(
                self.git_tag_config[mbed_version]["tag"], mingw_logger
            )
            self.set_config_on_code(mbed_version, mingw_logger)
            result = self.build_and_test_using_mingw(
                self.git_tag_config[mbed_version]["tag"], mingw_logger
            )
            self.mingw_results[mbed_version] = result
        except Exception as error:
            mingw_logger.error(error)

    def get_environment_containing_mingw_path(self):
        """This first checks if the mingw command exists on the path,
         if not it searches the system for the command
         and adds it to the path"""
        my_environment = os.environ.copy()
        if subprocess.call(["where", self.mingw_command]) != 0:
            if self.mingw_directory is None:
                for root, dirs, files in os.walk("C:\\"):
                    if self.mingw_directory is not None:
                        break
                    for name in files:
                        if name == self.mingw_command + ".exe":
                            self.mingw_directory = root
                            break
            my_environment["PATH"] = "{};{}".format(
                self.mingw_directory, my_environment["PATH"]
            )
        return my_environment

    def build_and_test_using_mingw(self, mbed_tag, logger):
        my_environment = self.get_environment_containing_mingw_path()
        my_environment["WINDOWS"] = "1"
        logger.info(
            "Building mbedTLS {} using {}".format(mbed_tag, self.mingw_command)
        )
        make_process = subprocess.Popen(
            ["cmd.exe"],
            env=my_environment,
            cwd=self.repository_path,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        make_process.stdin.write(self.mingw_command + " clean\n")
        make_process.stdin.write(self.mingw_command + " CC=gcc check\n")
        make_process.stdin.close()
        make_output, _ = make_process.communicate()
        logger.info(make_output)
        if (make_process.returncode == 0 and
                re.search(self.mingw_success_pattern, make_output)):
            return True
        else:
            return False

    def run_selftest_on_built_code(self, selftest_dir, logger):
        """Runs selftest.exe and checks that it reports all tests passing."""
        logger.info(selftest_dir)
        test_process = subprocess.Popen(
            ["{}\\{}".format(selftest_dir, self.selftest_exe)],
            cwd=selftest_dir,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        test_output, _ = test_process.communicate()
        logger.info(test_output)
        if (test_process.returncode == 0 and
                re.search(self.selftest_success_pattern, test_output)):
            return True
        else:
            return False

    def run_test_suites_on_built_code(self, solution_dir, test_run, logger):
        """Runs the various test suites and checks that they all pass"""
        msbuild_test_process = subprocess.Popen(
            ["cmd.exe"],
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
            "msbuild /p:Configuration={} /m RUN_TESTS.vcxproj\n".format(
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
            return True
        else:
            return False

    def get_environment_containing_VSCMD_START_DIR(self, solution_dir):
        """This is done to bypass a 'feature' added in Visual Studio 2017.
         If the %USERPROFILE%\Source directory exists, then running
         vcvarsall.bat will silently change directory to that directory.
         Setting the VSCMD_START_DIR environment variable causes it to change
         to that directory instead"""
        my_environment = os.environ.copy()
        my_environment["VSCMD_START_DIR"] = solution_dir
        return my_environment

    def build_code_using_visual_studio(self, solution_dir, test_run, logger):
        logger.info("Building mbedTLS using Visual Studio v{}".format(
            test_run.vs_version
        ))
        my_environment = self.get_environment_containing_VSCMD_START_DIR(
            solution_dir
        )
        if test_run.should_retarget:
            retarget = "v{}".format(
                self.vs_version_toolsets[test_run.vs_version]
            )
        else:
            retarget = "Windows7.1SDK"  # Workaround for missing 2010 x64 tools
        for solution_file in os.listdir(solution_dir):
            if re.search(self.solution_file_pattern, solution_file):
                break
        else:
            logger.error("Solution file missing")
            return False
        msbuild_process = subprocess.Popen(
            ["cmd.exe"],
            env=my_environment,
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
            "msbuild /t:Rebuild /p:Configuration={},Platform={},"
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
                build_result = "Build succeeded"
            else:
                build_result = "Build succeeded with warnings"
            self.visual_studio_results.append(
                VS_result(
                    test_run.vs_version,
                    test_run.configuration,
                    test_run.architecture,
                    "Yes" if test_run.should_retarget else "No",
                    self.git_tag_config[test_run.mbed_version]["tag"],
                    test_run.solution_type,
                    "Visual Studio build",
                    build_result
                )
            )
            return True
        else:
            self.visual_studio_results.append(
                VS_result(
                    test_run.vs_version,
                    test_run.configuration,
                    test_run.architecture,
                    "Yes" if test_run.should_retarget else "No",
                    self.git_tag_config[test_run.mbed_version]["tag"],
                    test_run.solution_type,
                    "Visual Studio build",
                    "Build failed"
                )
            )
            return False

    def build_visual_studio_solution_using_cmake(self, test_run, logger):
        solution_dir = self.repository_path + "\\cmake_solution"
        os.makedirs(solution_dir)
        cmake_process = subprocess.Popen(
            ["cmake", "-D", "ENABLE_TESTING=ON", "-G",
             "{}{}".format(
                 self.cmake_generators[test_run.vs_version],
                 self.cmake_architecture_flags[test_run.architecture]),
             ".."],
            cwd=solution_dir,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        cmake_output, _ = cmake_process.communicate()
        logger.info(cmake_output)
        return solution_dir

    def test_visual_studio_built_code(self, test_run):
        log_name = "VS{} {} {}{} {} {}".format(
            test_run.vs_version,
            test_run.configuration,
            test_run.architecture,
            " Retargeted" if test_run.should_retarget else "",
            self.git_tag_config[test_run.mbed_version]["tag"],
            test_run.solution_type
        )
        vs_logger = self.setup_logger(
            log_name, "{}\\{}.txt".format(self.log_dir, log_name)
        )
        try:
            self.set_repository_to_clean_state(
                self.git_tag_config[test_run.mbed_version]["tag"], vs_logger
            )
            self.set_config_on_code(test_run.mbed_version, vs_logger)
            if test_run.solution_type == "cmake":
                solution_dir = self.build_visual_studio_solution_using_cmake(
                    test_run, vs_logger
                )
            else:
                solution_dir = self.repository_path + "\\visualc\\VS2010\\"
            build_result = self.build_code_using_visual_studio(
                solution_dir, test_run, vs_logger
            )
            if build_result:
                if test_run.solution_type == "cmake":
                    test_suites_result = self.run_test_suites_on_built_code(
                        solution_dir, test_run, vs_logger
                    )
                    self.visual_studio_results.append(
                        VS_result(
                            test_run.vs_version,
                            test_run.configuration,
                            test_run.architecture,
                            "Yes" if test_run.should_retarget else "No",
                            self.git_tag_config[test_run.mbed_version]["tag"],
                            test_run.solution_type,
                            "test suites",
                            "Pass" if test_suites_result else "Fail"
                        )
                    )
                    selftest_code_path = "{}\\programs\\test\\{}".format(
                        solution_dir, test_run.configuration
                    )
                else:
                    selftest_code_path = "{}{}{}".format(
                        solution_dir,
                        "x64\\" if test_run.architecture == "x64" else "",
                        test_run.configuration
                    )
                selftest_result = self.run_selftest_on_built_code(
                    selftest_code_path, vs_logger
                )
                self.visual_studio_results.append(
                    VS_result(
                        test_run.vs_version,
                        test_run.configuration,
                        test_run.architecture,
                        "Yes" if test_run.should_retarget else "No",
                        self.git_tag_config[test_run.mbed_version]["tag"],
                        test_run.solution_type,
                        "selftest",
                        "Pass" if selftest_result else "Fail"
                    )
                )
        except Exception as error:
            vs_logger.error(error)

    def log_results(self):
        result_logger = self.setup_logger(
            "results", self.log_dir + "\\results.txt"
        )
        for version, result in self.mingw_results.iteritems():
            result_logger.info("MingW build {} in {}".format(
                "passed" if result else "failed", version
            ))
        result_table = PrettyTable(VS_result._fields)
        result_table.align[VS_result._fields[0]] = "l"
        for result in self.visual_studio_results:
            result_table.add_row(result)
        result_logger.info(result_table)

    def run_all_tests(self):
        try:
            for mbed_version in self.git_tag_config.keys():
                self.test_mingw_built_code(mbed_version)
            vs_test_runs = [
                VS_test_run(vs_version, configuration, architecture,
                            should_retarget, mbed_version, solution_type) for
                vs_version in self.visual_studio_versions for
                configuration in self.visual_studio_configurations for
                architecture in self.visual_studio_architectures for
                should_retarget in [False, True] for
                mbed_version in self.git_tag_config.keys() for
                solution_type in self.visual_studio_solution_types
            ]
            for vs_test_run in vs_test_runs:
                self.test_visual_studio_built_code(vs_test_run)
        finally:
            cleanup_logger = self.setup_logger(
                "cleanup", self.log_dir + "\\cleanup.txt"
            )
            self.set_repository_to_clean_state("master", cleanup_logger)
            self.log_results()


def run_main():
    parser = argparse.ArgumentParser(
        description='Test building mbedTLS on Windows.'
    )
    parser.add_argument(
        "repo_path", type=str, help="the path to the mbedTLS repository"
    )
    parser.add_argument(
        "log_path", type=str, help="the directory path for log files"
    )
    parser.add_argument(
        "-c", "--configuration-file", type=str,
        help="path to a json file containing the testing configurations"
    )

    windows_testing_args = parser.parse_args()
    with open(windows_testing_args.configuration_file) as f:
        testing_config = json.load(f)
    mbed_test = MbedWindowsTesting(
        windows_testing_args.repo_path,
        windows_testing_args.log_path,
        testing_config["git_tag_config"],
        testing_config["visual_studio_versions"],
        testing_config["visual_studio_configurations"],
        testing_config["visual_studio_architectures"]
    )
    mbed_test.run_all_tests()

if __name__ == "__main__":
    run_main()
