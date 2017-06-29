#!/usr/bin/perl
#
# Removes the license in a file for all .c and .h files.

use strict;

my $file = shift or die "Missing input file";

undef $/;

open(IP, "$file") or die "Opening $file: $!";
my $content = <IP>;
close(IP);

my $minimal_copyright = ' \*  Copyright \(C\) [\d\-,]+\s+[\w\s,\.]+(.*\n \*  All rights reserved.)?';

if ($content !~ /^$minimal_copyright/m) {
	die "No copyright notice found in $file!";
}

open(OP, ">$file") or die "Creating $file: $!";

my $matcher = "";

my $matcher_spdx = '\s*\*\s*SPDX-License-Identifier:.*\n';

if ($content =~ /^$matcher_spdx/m) {
	print " - Found SPDX in $file\n";
	$matcher = $matcher_spdx;

    print " - Removing SPDX identifier in $file\n";
    $content =~ s/^$matcher//m;
}
else {
    print " - No SPDX identifier found in $file\n";
}

$matcher = "";
my $matcher_gpl = ' \*  This program is free software(.|\n)*51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA\.\n';

if ($content =~ /^$matcher_gpl/m) {
	print " - Found GPL in $file\n";
	$matcher = $matcher_gpl;

    print " - Removing GPL license in $file\n";
    $content =~ s/^$matcher//m;
}
else {
    print " - No GPL license found in $file\n";
}

print OP $content;
close(OP);

print "Updated $file\n";
