#!/usr/bin/env python2

#  Copyright (c) 2018-2021, Arm Limited, All Rights Reserved
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

# This script requires python 2.7
# This script parses a callgrind output file and builds up the function call paths.
# It then combines this with the stack usage information from a .su file and gives
# the stack usage of all the paths traversed in the callgrind file.
# Note that when it is important to have the --separate-callers option set to a value
# higher than the maxmium call path in order to only consider actual paths taken,
# rather than theoretical paths which are never taken.

import re
import argparse

class Tree(dict):
    def __missing__(self, key):
        value = self[key] = type(self)()
        return value


class CallgrindPathParser(object):

    def __init__(self,
                 su_file_path,
                 callgrind_file_path,
                 output_file_path,
                 debug_file_path,
                 call_tree_file_path):
        self.su_file_path = su_file_path
        self.callgrind_file_path = callgrind_file_path
        self.output_file_path = output_file_path
        self.debug_file_path = debug_file_path
        self.call_tree_file_path = call_tree_file_path
        self.function_name_pattern = '.?fn=\((?P<function_number>\d+)\) (?P<function_name>[a-zA-Z0-9_\(\)]+)'
        self.function_number_pattern = '.?fn=\((?P<number>\d+)\)'
        self.file_name_pattern = '.?f[li]=\((?P<file_number>\d+)\) .+/(?P<file_name>[a-zA-Z0-9_]+\.c)'
        self.file_number_pattern = '.?f[li]=\((?P<number>\d+)\)'
        self.function_stack_costs = {}
        self.callgrind_number_to_file_map = {}
        self.callgrind_number_to_function_map = {}
        self.callgrind_function_file_map = {}
        self.function_call_map = {}
        self.stack_cost_paths = []
        self.main_function_number = None
        self.stack_tree = Tree()
        self.debug_costs = set()

    def print_tree_node(self, tree, call_tree_file, depth=0):
        for key, value in sorted(tree.items(), key=lambda x: x[0]):
            call_tree_file.write("  " * depth + self.callgrind_number_to_function_map[key] + "\n")
            if isinstance(value, Tree):
                self.print_tree_node(value, call_tree_file, depth + 1)

    def get_function_stack_costs(self):
        with open(self.su_file_path, "r") as su_file:
            su_content = su_file.read()
            for line in su_content.splitlines():
                line_content = line.split()
                if len(line_content) != 3:
                    continue
                function_details = line_content[0].split(":")
                if function_details[0] in self.function_stack_costs.keys():
                    self.function_stack_costs[function_details[0]][function_details[3]] = line_content[1]
                else:
                    self.function_stack_costs[function_details[0]] = {function_details[3]: line_content[1]}

    def get_callgrind_file_number_map(self):
        with open(self.callgrind_file_path) as callgrind_file:
            callgrind_content = callgrind_file.read()
            for line in callgrind_content.splitlines():
                match = re.match(self.file_name_pattern, line)
                if match:
                    self.callgrind_number_to_file_map[match.group('file_number')] = match.group('file_name')

    def get_callgrind_function_number_map(self):
        with open(self.callgrind_file_path) as callgrind_file:
            callgrind_content = callgrind_file.read()
            for line in callgrind_content.splitlines():
                match = re.match(self.function_name_pattern, line)
                if match:
                    self.callgrind_number_to_function_map[match.group('function_number')] = match.group('function_name')
                    if match.group('function_name') == 'main':
                        self.main_function_number = match.group('function_number')

    def get_callgrind_function_file_map(self):
        with open(self.callgrind_file_path) as callgrind_file:
            callgrind_content = callgrind_file.read()
            for callgrind_block in callgrind_content.split("\n\n"):
                block_file_number = None
                block_function_number = None
                temp_file_number = None
                temp_file_name = None
                first_line = True
                for line in callgrind_block.splitlines():
                    if first_line:
                        if line.startswith("ob"):
                            continue
                        first_line = False
                        function_match = re.match(self.function_number_pattern, line)
                        if function_match:
                            block_function_number = function_match.group('number')
                            continue
                        file_match = re.match(self.file_number_pattern, line) 
                        if file_match:
                            block_file_number = file_match.group('number')
                            continue
                    file_match = re.match(self.file_number_pattern, line)
                    if file_match:
                        try:
                            temp_file_name = self.callgrind_number_to_file_map[file_match.group('number')]
                            if temp_file_name in self.function_stack_costs.keys():
                                temp_file_number = file_match.group('number')
                        except KeyError:
                            temp_file_number = None
                            temp_file_name = None
                        continue
                    function_match = re.match(self.function_number_pattern, line)
                    if function_match:
                        function_number = function_match.group('number')
                        function_name = self.callgrind_number_to_function_map[function_number]
                        if temp_file_number is not None:
                            if function_name in self.function_stack_costs[temp_file_name].keys():
                                self.callgrind_function_file_map[function_number] = temp_file_number
                                temp_file_number = None
                                temp_file_name = None
                                continue
                            temp_file_number = None
                            temp_file_name = None
                        if block_file_number is not None:
                            self.callgrind_function_file_map[function_number] = block_file_number
                            continue
                        if block_function_number is not None:
                            self.callgrind_function_file_map[function_number] = "function " + block_function_number
                            continue
        for key, value in self.callgrind_function_file_map.iteritems():
            while "function" in value:
                value = self.callgrind_function_file_map[value[9:]]
            self.callgrind_function_file_map[key] = value


    def get_function_call_map(self):
        with open(self.callgrind_file_path) as callgrind_file:
            callgrind_content = callgrind_file.read()
            for callgrind_block in callgrind_content.split("\n\n"):
                function_number = None
                function_calls = set()
                for line in callgrind_block.splitlines():
                    if line.startswith("fn="):
                        function_number = re.match('fn=\((?P<number>\d+)\)', line).group('number')
                    if line.startswith("cfn="):
                        function_calls.add(re.match('cfn=\((?P<number>\d+)\)', line).group('number'))
                if function_number is not None:
                    self.function_call_map[function_number] = function_calls

    def add_nodes(self, current_node, current_function_number, function_call_list):
        for next_function_number in self.function_call_map[current_function_number]:
            if self.callgrind_number_to_function_map[next_function_number].startswith("_"):
                continue
            new_function_call_list = function_call_list[:]
            new_function_call_list.append(next_function_number)
            self.add_nodes(
                current_node[next_function_number],
                next_function_number,
                new_function_call_list
            )

    def get_stack_cost_from_function_number(self, function_number):
        try:
            file_name = self.callgrind_number_to_file_map[
                self.callgrind_function_file_map[function_number]
            ]
        except KeyError:
            file_name = "Error"
        try:
            function_name = self.callgrind_number_to_function_map[function_number]
        except KeyError:
            function_name = "Error"
        try:
            cost = int(self.function_stack_costs[file_name][function_name])
        except KeyError:
            cost = 0
        self.debug_costs.add((function_number, file_name, function_name, cost))
        return cost

    def get_node_cost(self, current_node, current_path, current_cost):
        for function_number, function_calls in current_node.items():
            new_current_path = current_path + "->{}: {}".format(
                self.callgrind_number_to_function_map[function_number],
                self.get_stack_cost_from_function_number(function_number)
            )
            new_current_cost = current_cost + self.get_stack_cost_from_function_number(function_number)
            if function_calls.keys() == []:
                self.stack_cost_paths.append((new_current_path, new_current_cost))
            self.get_node_cost(function_calls, new_current_path, new_current_cost)

    def create_stack_tree(self):
        self.add_nodes(
            self.stack_tree[self.main_function_number],
            self.main_function_number,
            [self.main_function_number]
        )

    def get_stack_cost_paths(self):
        self.get_node_cost(self.stack_tree, "", 0)

    def print_tree(self):
        if self.call_tree_file_path:
            with open(self.call_tree_file_path, "w") as call_tree_file:
                self.print_tree_node(self.stack_tree, call_tree_file)

    def print_path_costs(self):
        with open(self.output_file_path, "w") as output_file:
            for path in sorted(self.stack_cost_paths, key=lambda x: x[1]):
                output_file.write("Total stack usage: {}B\n".format(path[1]))
                lines = path[0].split('->')
                for i in xrange(1, len(lines)):
                     output_file.write(
                        " " * 2 * i + lines[i].split()[0] +
                        " " * max(100 - 2*i - len(lines[i].split()[0]), 1) + lines[i].split()[1] + "\n"
                    )
                output_file.write("\n")

    def print_debug(self):
        if self.debug_file_path:
            with open(self.debug_file_path, "w") as debug_file:
                debug_file.write("number_to_file_map:\n")
                for k,v in self.callgrind_number_to_file_map.iteritems():
                    debug_file.write("{} - {}\n".format(k,v))
                debug_file.write("number_to_function_map:\n")
                for k,v in self.callgrind_number_to_function_map.iteritems():
                    debug_file.write("{} - {}\n".format(k,v))
                debug_file.write("function_to_file_map:\n")
                for k,v in self.callgrind_function_file_map.iteritems():
                    debug_file.write("{} - {}\n".format(k,v))
                for cost in sorted(self.debug_costs, key=lambda x: x[1], reverse=True):
                    if cost[3] == 0:
                        debug_file.write(" : ".join(map(str, cost)) + "\n")


def run_main():
    parser = argparse.ArgumentParser(
        description='Merge .su files into a single file.'
    )
    parser.add_argument(
        "callgrind_file", type=str, help="the path to the callgrind file"
    )
    parser.add_argument(
        "su_file", type=str, help="the path to the stack usage file"
    )
    parser.add_argument(
        "output_file_path", type=str, help="the output file to be written"
    )
    parser.add_argument(
        "--debug_file", type=str, help="debug output file if desired"
    )
    parser.add_argument(
        "--call_tree_file_path", type=str, help="call tree output file if desired"
    )
    stack_usage_args = parser.parse_args()
    path_parser = CallgrindPathParser(
        stack_usage_args.su_file,
        stack_usage_args.callgrind_file,
        stack_usage_args.output_file_path,
        stack_usage_args.debug_file,
        stack_usage_args.call_tree_file_path
    )
    path_parser.get_function_stack_costs()
    path_parser.get_callgrind_file_number_map()
    path_parser.get_callgrind_function_number_map()
    path_parser.get_callgrind_function_file_map()
    path_parser.get_function_call_map()
    path_parser.create_stack_tree()
    path_parser.get_stack_cost_paths()
    path_parser.print_path_costs()
    path_parser.print_tree()
    path_parser.print_debug()


if __name__ == "__main__":
    run_main()
