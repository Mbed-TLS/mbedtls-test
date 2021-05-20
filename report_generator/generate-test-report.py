#! /usr/bin/env python

#  Copyright (c) 2016-2021, Arm Limited, All Rights Reserved
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

import csv
import argparse
import sys
import subprocess
import os
import time

class ReportGenerator:
    # This script expects the CSV file to contain the following fields
    TEST_NO           = "Number"
    TEST_NAME         = "Name"
    TEST_DEPENDENCIES = "Dependencies"
    TEST_RESULT       = "Result"
    TEST_REASON       = "Reason"
    TEST_LOG_FILE     = "Log file"
    TEST_SCRIPT       = "Test script"

    # These strings are the possible values for the TEST_RESULT column
    TEST_RESULT_PASS = "PASS"
    TEST_RESULT_FAIL = "FAIL"
    TEST_RESULT_SKIP = "SKIP"

    # Tools used when creating the pdf report
    TOOL_PANDOC = "pandoc"
    TOOL_LOWRITER = "lowriter"

    # Template for the pdf report in docx format
    PANDOC_DOCX_TEMPLATE = "pandoc-mbed-tls-template.docx"

    # Template for the markdown report
    MARKDOWN_TEMPLATE = "markdown-mbed-tls-template.md"

    DOCX_EXT = ".docx"
    MD_EXT = ".md"

    # Metadata information for the report
    REPORT_CONFIDENTIALITY = "Confidential Restricted"
    ARM_DIVISION = "IOTBU"

    def __init__(self, csv_file, csv_delimiter):
        self.csv_file = csv_file
        self.csv_delimiter = csv_delimiter
        self.csv_data = None

        self.read_csv()

    # Print output to a file
    def println(self, *args):
        msg = " ".join(map(str, args))
        self.output_stream.write(msg + os.linesep)

    def unquote_str(self, quoted):
        return quoted.decode("unicode_escape").encode("ascii")

    def read_csv(self):
        with open (self.csv_file, "rb") as csv_file_stream:
            csv_reader = csv.reader(csv_file_stream,
                delimiter=self.csv_delimiter, quotechar="'")

            # Extract the header from the csv file
            header = map(self.unquote_str, csv_reader.next())
            self.csv_data = dict(zip(header, [[] for i in range(len(header))]))

            for row in csv_reader:
                for col, quoted_val in zip(header, row):
                    unquoted_val = self.unquote_str(quoted_val)
                    self.csv_data[col].append(unquoted_val)

    def shorten_path(self, file_path):
        path, file_name = os.path.split(file_path)
        if len(path) > 0:
            return os.path.join(os.path.split(path)[1], file_name)
        else:
            return file_name

    def extract_detailed(self):
        make_tests_dict = lambda keys: \
            dict(zip(keys, [[] for i in range(len(keys))]))

        num_tests = len(self.csv_data[ReportGenerator.TEST_NO])

        failed_keys = [ReportGenerator.TEST_NO, ReportGenerator.TEST_SCRIPT,
            ReportGenerator.TEST_NAME, ReportGenerator.TEST_LOG_FILE]
        failed_tests = make_tests_dict(failed_keys)

        passed_keys = [ReportGenerator.TEST_NO, ReportGenerator.TEST_SCRIPT,
            ReportGenerator.TEST_NAME]
        passed_tests = make_tests_dict(passed_keys)

        skipped_keys = [ReportGenerator.TEST_NO, ReportGenerator.TEST_SCRIPT,
            ReportGenerator.TEST_NAME, ReportGenerator.TEST_DEPENDENCIES,
            ReportGenerator.TEST_REASON]
        skipped_tests = make_tests_dict(skipped_keys)

        for i in range(num_tests):
            if self.csv_data[ReportGenerator.TEST_RESULT][i] == \
                ReportGenerator.TEST_RESULT_PASS:
                passed_tests[ReportGenerator.TEST_NAME].append(
                    self.csv_data[ReportGenerator.TEST_NAME][i])
                passed_tests[ReportGenerator.TEST_NO].append(
                    self.csv_data[ReportGenerator.TEST_NO][i])
                passed_tests[ReportGenerator.TEST_SCRIPT].append(
                    self.shorten_path(
                    self.csv_data[ReportGenerator.TEST_SCRIPT][i]))
            elif self.csv_data[ReportGenerator.TEST_RESULT][i] == \
                ReportGenerator.TEST_RESULT_SKIP:
                skipped_tests[ReportGenerator.TEST_NAME].append(
                    self.csv_data[ReportGenerator.TEST_NAME][i])
                skipped_tests[ReportGenerator.TEST_NO].append(
                    self.csv_data[ReportGenerator.TEST_NO][i])
                skipped_tests[ReportGenerator.TEST_DEPENDENCIES].append(
                    self.csv_data[ReportGenerator.TEST_DEPENDENCIES][i])
                skipped_tests[ReportGenerator.TEST_REASON].append(
                    self.csv_data[ReportGenerator.TEST_REASON][i])
                skipped_tests[ReportGenerator.TEST_SCRIPT].append(
                    self.shorten_path(
                    self.csv_data[ReportGenerator.TEST_SCRIPT][i]))
            else:
                failed_tests[ReportGenerator.TEST_NO].append(
                    self.csv_data[ReportGenerator.TEST_NO][i])
                failed_tests[ReportGenerator.TEST_NAME].append(
                    self.csv_data[ReportGenerator.TEST_NAME][i])
                failed_tests[ReportGenerator.TEST_LOG_FILE].append(
                    self.csv_data[ReportGenerator.TEST_LOG_FILE][i])
                failed_tests[ReportGenerator.TEST_SCRIPT].append(
                    self.shorten_path(
                    self.csv_data[ReportGenerator.TEST_SCRIPT][i]))

        return (failed_keys, failed_tests, passed_keys, passed_tests,
            skipped_keys, skipped_tests)

    def extract_summary(self):
        num_tests = len(self.csv_data[ReportGenerator.TEST_NO])
        count = lambda col, status: sum([1 for i in range(num_tests) \
            if self.csv_data[col][i] == status])
        passed = count(ReportGenerator.TEST_RESULT,
            ReportGenerator.TEST_RESULT_PASS)
        failed = count(ReportGenerator.TEST_RESULT,
            ReportGenerator.TEST_RESULT_FAIL)
        skipped = count(ReportGenerator.TEST_RESULT,
            ReportGenerator.TEST_RESULT_SKIP)

        if passed + failed + skipped != num_tests:
            raise Exception("CSV data contains unrecognised values for "
                "{0}".format(TEST_RESULT))

        return (passed, failed, skipped)

    def print_ascii_summary(self):
        passed, failed, skipped = self.extract_summary()

        self.println("Test Report Summary for '{0}'".format(self.csv_file))
        self.println("Total tests:", passed + failed + skipped)
        self.println("Total passed:", passed)
        self.println("Total failed:", failed)
        self.println("Total skipped:", skipped)
        self.println("Total executed tests:", passed + failed)
        self.println("")


    def print_ascii_detailed(self):
        failed_keys, failed_tests, passed_keys, passed_tests, skipped_keys, \
            skipped_tests = self.extract_detailed()

        self.println("Test Report Detailed Overview for '{0}'".format(self.csv_file))
        self.println("Failed tests:")
        self.print_ascii_table(failed_keys, failed_tests)

        self.println("")
        self.println("Skipped tests:")
        self.print_ascii_table(skipped_keys, skipped_tests)

        self.println("")
        self.println("Passed tests:")
        self.print_ascii_table(passed_keys, passed_tests)

        self.println("")

    def print_ascii_table(self, keys, data):
        format_val = lambda width, val: str(" {:" + str(width) +
            "} ").format(val)
        format_header = lambda key: format_val(col_widths[key], key)
        format_body = lambda key: format_val(col_widths[key], data[key][i])
        make_table_divider = lambda: "+{0}+".format(
            "+".join(map(lambda l: (l + 2) * '-', [col_widths[key] \
            for key in keys])))

        # Find out the width of each column in the table
        col_widths = {}
        for key in keys:
            col_widths[key] = max(len(str(val)) for val in [key] + data[key])

        # Print the table header
        self.println(make_table_divider())
        self.println("|{0}|".format("|".join(map(format_header, keys))))
        self.println(make_table_divider())

        num_tests = len(data[keys[0]])
        for i in range(num_tests):
            self.println("|{0}|".format("|".join(map(format_body, keys))))

        # Add the end of table
        self.println(make_table_divider())

    def print_ascii(self, output_file):
        with open(output_file, "w") as self.output_stream:
            self.print_ascii_summary()
            self.print_ascii_detailed()

    def print_pdf(self, output_pdf_file, author, email, report_number):
        output_filename, _ = os.path.splitext(output_pdf_file)
        output_dir = os.path.dirname(output_pdf_file)
        output_md_file = output_filename + ReportGenerator.MD_EXT
        output_docx_file = output_filename + ReportGenerator.DOCX_EXT

        markdown_title = ""
        markdown_body = ""
        with open(ReportGenerator.MARKDOWN_TEMPLATE, "r") as markdown_stream:
            markdown_title = markdown_stream.readline().strip()
            markdown_body = markdown_stream.read().strip()

        # Generate .md file here
        with open(output_md_file, "w") as self.output_stream:
            self.println(markdown_title)
            self.println("")
            self.print_md_metadata(author, email, report_number)
            self.println("")
            self.println(markdown_body)
            self.println("")
            self.print_md_summary()
            self.print_md_detailed()

        # Generate docx document using pandoc
        pandoc_cmd = [ReportGenerator.TOOL_PANDOC, "-S", "--reference-docx",
            ReportGenerator.PANDOC_DOCX_TEMPLATE, "-f",
            "markdown+multiline_tables", "--number-sections", "-o",
            output_docx_file, output_md_file]
        pandoc_proc = subprocess.Popen(args=pandoc_cmd)
        pandoc_exit_code = pandoc_proc.wait()
        if pandoc_exit_code != 0:
            raise subprocess.CalledProcessError(pandoc_exit_code, pandoc_cmd,
                output=None)

        # Generate the pdf document using LibreOffice Writer
        lowriter_cmd = [ReportGenerator.TOOL_LOWRITER, "--headless",
            "--convert-to", "pdf:writer_pdf_Export", "--outdir", output_dir,
            output_docx_file]
        lowriter_proc = subprocess.Popen(args=lowriter_cmd)
        lowriter_exit_code = lowriter_proc.wait()
        if lowriter_exit_code != 0:
            raise subprocess.CalledProcessError(lowriter_exit_code,
                lowriter_cmd, output=None)

    def print_md_metadata(self, author, email, report_number):
        table_keys = ["key", "val"]
        table_data = {"key": ["Document number",
                              "Division",
                              "Date of issue",
                              "Author",
                              "Confidentiality"],
                      "val": [report_number,
                              ReportGenerator.ARM_DIVISION,
                              time.strftime("%d/%m/%Y"),
                              "{0} ({1})".format(author, email),
                              ReportGenerator.REPORT_CONFIDENTIALITY]}

        self.print_md_table(table_keys, table_data, headerless=True)
        self.println("")

    def print_md_summary(self):
        passed, failed, skipped = self.extract_summary()

        self.println("## Test Report Summary")
        self.println("")

        table_keys = ["", "Total"]
        table_data = {"": [ReportGenerator.TEST_RESULT_PASS,
                           ReportGenerator.TEST_RESULT_FAIL,
                           ReportGenerator.TEST_RESULT_SKIP,
                           "Tests"],
                      "Total": [passed,
                                failed,
                                skipped,
                                passed + failed + skipped]}
        self.print_md_table(table_keys, table_data)
        self.println("")

    def print_md_detailed(self):
        failed_keys, failed_tests, passed_keys, passed_tests, skipped_keys, \
            skipped_tests = self.extract_detailed()

        self.println("## Test Report Detailed Overview")
        self.println("")

        self.println("### Failed tests")
        self.println("")
        if len(failed_tests[failed_keys[0]]) < 1:
            self.println("There are no failed tests")
        else:
            self.print_md_table(failed_keys, failed_tests)
        self.println("")

        self.println("### Skipped tests")
        self.println("")
        if len(skipped_tests[skipped_keys[0]]) < 1:
            self.println("There are no skipped tests")
        else:
            self.print_md_table(skipped_keys, skipped_tests)
        self.println("")

        self.println("### Passed tests")
        self.println("")
        if len(passed_tests[passed_keys[0]]) < 1:
            self.println("There are no passed tests")
        else:
            self.print_md_table(passed_keys, passed_tests)
        self.println("")

    def print_md_table(self, keys, data, headerless=False):
        format_val = lambda width, val: str("{:" + str(width) +
            "} ").format(val)
        format_header = lambda key: format_val(col_widths[key], key)
        format_body = lambda key: format_val(col_widths[key], data[key][i])
        make_table_divider = lambda: " ".join(map(
            lambda l: (l + 1) * '-', [col_widths[key] for key in keys]))

        # Find out the width of each column in the table
        col_widths = {}
        for key in keys:
            col_widths[key] = max(len(str(val)) for val in [key] + data[key])

        # Print the table header
        self.println(make_table_divider())
        if not headerless:
            self.println(" ".join(map(format_header, keys)))
            self.println(make_table_divider())

        num_tests = len(data[keys[0]])
        for i in range(num_tests):
            if i > 0:
                self.println("")
            self.println(" ".join(map(format_body, keys)))

        # Add the end of table
        self.println(make_table_divider())

def main(args):
    # This is just for safety to ensure that we do not misinterpret any paths
    abspath = lambda path: os.path.abspath(os.path.expanduser(path))


    reporter = ReportGenerator(abspath(args.csv_file), args.csv_delimiter)
    if args.output_ascii_file is not None:
        print "Writing ascii report to '{0}'".format(args.output_ascii_file)
        reporter.print_ascii(abspath(args.output_ascii_file))
    if args.output_pdf_file is not None:
        print "Writing pdf report to '{0}'".format(args.output_pdf_file)
        reporter.print_pdf(abspath(args.output_pdf_file), args.author,
            args.email, args.report_number)
    print "DONE"

def parse_cmdline_args(args):
    parser = argparse.ArgumentParser(description="This program processes "
        "raw test output information in CSV format and generates test "
        "reports.")

    # Add the script options
    parser.add_argument("-f", "--csv-file", action="store", type=str,
        required=True, help="Path to a file in CSV format containing the raw "
        "test output", metavar="PATH")
    parser.add_argument("-d", "--csv-delimiter", action="store",
        type=str, required=False, default=",", help="The separator character "
        "in the CSV file", metavar="DELIM")
    parser.add_argument("-a", "--output-ascii-file", action="store", type=str,
        required=False, default=None, help="File where the processed data "
        "will be written in ascii format", metavar="PATH")
    parser.add_argument("-p", "--output-pdf-file", action="store", type=str,
        required=False, default=None, help="File where the processed data "
        "will be written in pdf format", metavar="PATH")
    parser.add_argument("-u", "--author", action="store", type=str,
        required=False, default="n/a", help="Author of the report",
        metavar="AUTHOR")
    parser.add_argument("-e", "--email", action="store", type=str,
        required=False, default="n/a", help="Author's email address",
        metavar="EMAIL")
    parser.add_argument("-n", "--report-number", action="store", type=str,
        required=False, default="n/a", help="Report number", metavar="NUMBER")

    return parser.parse_args()

if __name__ == "__main__":
    args = parse_cmdline_args(sys.argv)
    main(args)
