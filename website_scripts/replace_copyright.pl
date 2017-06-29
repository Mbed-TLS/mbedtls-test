#!/usr/bin/perl
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
