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
# Changes the copyright for all .c anc .h files.

use strict;

my $file = shift or die "Missing input file";

undef $/;

open(IP, "$file") or die "Opening $file: $!";
my $content = <IP>;
close(IP);

my $minimal_copyright = ' \*  Copyright \(C\) [\d\-,]+\s+((.*\n)* \*\n \*  This file is part of mbed TLS.*\n)?((.*\n)* \*  All rights reserved.)?';
my $full_copyright_match = ' \*  Copyright \(C\) 2006-2015, ARM Limited, All Rights Reserved\n \*\n \*  This file is part of mbed TLS \(https://tls\.mbed\.org\)';
my $full_copyright = " *  Copyright (C) 2006-2015, ARM Limited, All Rights Reserved\n *\n *  This file is part of mbed TLS (https://tls.mbed.org)";

if ($content !~ /^$minimal_copyright/m) {
	die "No copyright notice found in $file!";
}

if ($content !~ /^$full_copyright_match/m) {
	print " - Fixing incorrect copyright in $file\n";

    $content =~ s/^$minimal_copyright(.*)$/$full_copyright/m;

    open(OP, ">$file") or die "Creating $file: $!";
    print OP $content;
    close(OP);

    print "Updated $file\n";
}
