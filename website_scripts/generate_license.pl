#!/usr/bin/perl
#
# Generates the LICENSE file

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

open(OP, ">$file") or die "Creating $file: $!";
print OP $license;
close(OP);

print "Updated $file\n";
