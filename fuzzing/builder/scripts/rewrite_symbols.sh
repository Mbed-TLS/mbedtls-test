#!/bin/bash
#
# Copyright (C) 2017, ARM Limited, All Rights Reserved
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This file is part of mbed TLS (https://tls.mbed.org)

# Rewrites mbedtls b version symbols and creates fat archives containing both
# versions

set -eu

top="$(dirname $1)/.."

if [ ! -d "$top/b/" ]; then
    echo "No B version found"
    ln -s . /usr/local/fat
    exit 0
fi

for d in $@; do
    b_dir=$(dirname $d)/../b/$(basename $d)
    nm $b_dir/lib/* | egrep " (T|R|D) " | cut -d' ' -f3 | sed 's/mbedtls\(.*\)/--redefine-sym \0=b_\0/' | xargs > /tmp/rewrites.txt
    for f in $b_dir/lib/*; do
        objcopy $(cat /tmp/rewrites.txt) $f
    done
done

for d in $@; do
    b_dir=$(dirname $d)/../b/$(basename $d)
    fat_dir=$(dirname $d)/../fat/$(basename $d)
    mkdir -p $fat_dir/lib
    for f in $d/lib/*; do
        b_f=$b_dir/lib/$(basename $f)
        echo -e "create $fat_dir/lib/$(basename $f)\naddlib ${f}\naddlib ${b_f}\nsave\nend\n" | ar -M
    done
done
