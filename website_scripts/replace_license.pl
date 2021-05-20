#!/usr/bin/perl
#
#  Copyright (c) 2017-2021, ARM Limited, All Rights Reserved
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
#
# Changes the license in a file to GPL or a commercial license for all .c anc .h files.

use strict;

my $licenses_dir = shift or die "Missing license directory";
my $file = shift or die "Missing input file";
my $client_name = shift or die "Missing client_name for license";
my $client_products = shift or die "Missing client_products for license";
my $client_license = shift or die "Missing client_license for license";

undef $/;

sub load_template_license()
{
    if ( ! -r "$licenses_dir/license-template.txt" )
    {
        die("License file not present.");
    }

    open(LICENSE_FILE, "$licenses_dir/license-template.txt") or die "Opening template: $!";
    my $text = <LICENSE_FILE>;
    close(LICENSE_FILE);

    return $text;
}

my $license = load_template_license();
$license =~ s/CLIENT_NAME/$client_name/gm;
$license =~ s/CLIENT_PRODUCTS/$client_products/gm;
$license =~ s/CLIENT_LICENSE/$client_license/gm;
$license =~ s/^/ *  /gm;

open(IP, "$file") or die "Opening $file: $!";
my $content = <IP>;
close(IP);

my $minimal_copyright = ' \*  Copyright \(C\) [\d\-,]+\s+[\w\s,\.]+(.*\n \*  All rights reserved.)?';

if ($content !~ /^$minimal_copyright/m) {
	die "No copyright notice found in $file!";
}

open(OP, ">$file") or die "Creating $file: $!";

my $matcher = "";

my $matcher_gpl = ' \*  This program is free software(.|\n)*51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA\.\n';

if ($content =~ /^$matcher_gpl/m) {
	print " - Found GPL in $file\n";
	$matcher = $matcher_gpl;
}

print " - Fixing license for $client_name in $file\n";
$content =~ s/^$matcher/$license/m;

print OP $content;
close(OP);

print "Updated $file\n";
