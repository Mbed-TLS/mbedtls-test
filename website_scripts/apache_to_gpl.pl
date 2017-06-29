#!/usr/bin/perl
#
# Changes the license in a file from Apache to GPL

use strict;

my $licenses_dir = shift or die "Missing licenses directory";
my $file = shift or die "Missing input file";
undef $/;

sub load_gpl_header()
{
    if ( ! -r "$licenses_dir/gpl-header.txt" )
    {
        die("License file not present.");
    }

    open(LICENSE_FILE, "$licenses_dir/gpl-header.txt") or die "Opening template: $!";
    my $text = <LICENSE_FILE>;
    close(LICENSE_FILE);

    return $text;
}

my $license = load_gpl_header();

open(IP, "$file") or die "Opening $file: $!";
my $content = <IP>;
close(IP);

my $minimal_copyright = ' \*  Copyright \(C\)';

if ($content !~ /^$minimal_copyright/m) {
	die "No copyright notice found in $file!";
}

open(OP, ">$file") or die "Creating $file: $!";

my $matcher_apache = ' \*  SPDX-License-Identifier: Apache-2\.0(.|\n)*limitations under the License\.\n';

if ($content =~ /^$matcher_apache/m) {
    $content =~ s/^$matcher_apache/$license/m;
}
else {
    print "Warning! No apache found in $file!";
}

print OP $content;
close(OP);

print "Updated $file\n";
