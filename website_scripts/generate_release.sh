#!/bin/sh
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

if [ ! -d "files/relicensed/git_repos" ];
then
    git clone --bare https://github.com/ARMmbed/mbedtls files/relicensed/git_repos
    ( cd files/relicensed/git_repos && git remote add private git@github.com:ARMmbed/mbedtls-restricted )
fi

if [ ! -d "files/relicensed/git_repos" ];
then
    echo "files/relicensed/git_repos does not exist."
    exit 1
fi

( cd files/relicensed/git_repos && git fetch --all && git fetch --tags )

CORE_APACHE=no
PRIMARY=no
MAINSTREAM=yes
VERSION=mbedtls-1.3.20
RAW_VERSION=1.3.20
RAW_PRODUCT="mbed TLS"
RAW_TYPE=mbedtls
REMOVE_VERSION=mbedtls-1.3.19
REMOVE_TYPE=mbedtls
mkdir -p "files/relicensed/$VERSION" || exit 1
( cd "files/relicensed/git_repos" && git archive "$VERSION" ) | tar -x -C "files/relicensed/$VERSION" || exit 1

if [ "Xyes" = "X$CORE_APACHE" ];
then
  ( cd "files/relicensed" && tar zcf "$VERSION-apache.tgz" "$VERSION" )
  mv "files/relicensed/$VERSION-apache.tgz" "files"

  # Relicense to GPL
  cp "scripts/gpl-2.0.txt" "files/relicensed/$VERSION"
  rm "files/relicensed/$VERSION/apache-2.0.txt"
  rm -r "files/relicensed/$VERSION/yotta"
  echo "This package of mbed TLS is specifically licensed under the GPL 2.0," > "files/relicensed/$VERSION/LICENSE"
  echo "as can be found in: gpl-2.0.txt" >> "files/relicensed/$VERSION/LICENSE"

  CMD="find \"files/relicensed/$VERSION\" '(' -name '*.c' -o -name '*.h' -o -name '*.cpp' -o -name '*.fmt' ')' -print -exec \"scripts/apache_to_gpl.pl\" \"scripts\" {} \; || exit 1"
  eval "$CMD"

  ( cd "files/relicensed" && tar zcf "$VERSION-gpl.tgz" "$VERSION" )
  mv "files/relicensed/$VERSION-gpl.tgz" "files"
else
  ( cd "files/relicensed" && tar zcf "$VERSION-gpl.tgz" "$VERSION" )
  mv "files/relicensed/$VERSION-gpl.tgz" "files"
fi

( cd "files/relicensed" && rm -rf "$VERSION" )

git add files/$VERSION-*.tgz

if [ "Xyes" = "X$MAINSTREAM" ];
then
  cp files/$VERSION-*.tgz "htdocs/code/download"
  git add htdocs/code/download/$VERSION-*.tgz

  if [ "X" != "X$REMOVE_VERSION" ];
  then
    git rm htdocs/code/download/${REMOVE_VERSION}-*.tgz
  fi

  ( cd htdocs/code/download && md5sum *.tgz > MD5SUMS )
  ( cd htdocs/code/download && sha1sum *.tgz > SHA1SUMS )
  ( cd htdocs/code/download && sha256sum *.tgz > SHA256SUMS )
  ( cd htdocs/code/download && git add MD5SUMS SHA1SUMS SHA256SUMS )
fi

if [ "Xyes" = "X$PRIMARY" ];
then
    if [ ! -d "doxygen/polarssl" ];
    then
        git submodule add git://github.com/ARMmbed/mbedtls doxygen/polarssl
    fi

    ( cd doxygen/polarssl && git fetch --all && git fetch --tags )
    ( cd doxygen/polarssl && git reset --hard && git checkout "$VERSION" )
    # use a full configuration in order to get doc for all config.h options
    ( cd doxygen/polarssl && scripts/config.pl realfull )
    git add doxygen/polarssl
    git rm -f doxygen/${REMOVE_TYPE}-*
    ( cd doxygen && ln -s polarssl "$VERSION" )
    cat doxygen/polarssl_site.doxyfile |                        \
        sed -e "s/polarssl\-[^\/]\+/$VERSION/" |                \
        sed -e "s/mbedtls\-[^\/]\+/$VERSION/" |                 \
        sed -e "s/PolarSSL v[^\"]\+/$RAW_PRODUCT v$RAW_VERSION/" | \
        sed -e "s/mbed TLS v[^\"]\+/$RAW_PRODUCT v$RAW_VERSION/"\
        > tmp
    mv tmp doxygen/polarssl_site.doxyfile
    git add doxygen/polarssl_site.doxyfile
    ( cd doxygen && doxygen polarssl_site.doxyfile )
    git add doxygen/${VERSION}
fi

echo "You can copy-paste the following in the release notes:"

for f in files/$VERSION-*.tgz; do
    echo ""
    echo "The hashes for $f are:"
    echo ""
    echo "~~~~~"
    echo "SHA-1: "; sha1sum $f | cut -d' ' -f1
    echo "SHA-256: "; sha256sum $f | cut -d' ' -f1
    echo "~~~~~"
done

echo "";
echo "You still have to do includes/download_common.inc.php yourself!!"

