# Website scripts

This directory contains a scripts originally designed to help automate the mbed TLS release process. The remaining of this document briefly describes the purpose of each script.

- `apache_to_gpl.pl`: Change the license in a file from Apache to GPL.
- `forum_activity.inc.php`: Gather some statistics regarding the posts in the mbed TLS forum.
- `generate_license.pl`: Generate a comercial license using the template in `license-template.txt`.
- `generate_release.sh`: Download the mbed TLS source code from GitHub and change the license to GPL. Furthermore, generate the tarball hashes to be pasted in the website and release notes.
- `mailinglist_send.inc.php`: Send a campaign email to the registered users in the mbed TLS website.
- `make_mbed_package.sh`: Prepare an mbed partner release.
- `make_package.sh`: Make a Polarssl package to be shipped to a customer.
- `remove_license.pl`: Remove the SPDX-License-Identifier and the GPL license header from a source code file.
- `replace_copyright.pl`: Replace the copyright notice from a source code file.
- `replace_license.pl`: Change the license in a source code file to GPL or a comercial license.
