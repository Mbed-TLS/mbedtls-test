#!/bin/bash
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

GIT_REPOS="git://github.com/polarssl/polarssl"
PROJECT="polarssl"
TAG=""
BASE=""
CLIENT_NAME=""
LICENSE_NAME=""
LICENSE_FOR=""
ONLY_FILES=""

# Parse arguments
#
until [ -z "$1" ]
do
  case "$1" in
    -b|--base)
      # Base dir used
      shift
      BASE=$1
      ;;
    -t|--tag)
      # Tag used
      shift
      TAG=$1
      ;;
    -c|--client-name)
      # Client name used
      shift
      CLIENT_NAME=$1
      ;;
    -l|--license-name)
      # License name used
      shift
      LICENSE_NAME=$1
      ;;
    -p|--license-for)
      # License for used
      shift
      LICENSE_FOR=$1
      ;;
    -o|--only-files)
      # Only files used
      shift
      ONLY_FILES=$1
      ;;
    -v|--verbose)
      # Be verbose
      VERBOSE="1"
      ;;
    -h|--help)
      # print help
      echo "Usage: $0"
      echo -e "  -b|--base\t\tBase dir of polarssl_licensing root."
      echo -e "  -c|--client-name\tClient name."
      echo -e "  -h|--help\t\tPrint this help."
      echo -e "  -l|--license-name\tLicense name (e.g. PL-CUSTOMER-NUMBER)"
      echo -e "  -o|--only-files\tSpace seperated list (e.g. 'library/aes.c include/polarssl/aes.h')"
      echo -e "  -p|--license-for\tLicensed products."
      echo -e "  -t|--tag\t\tTag to export and package."
      echo -e "  -v|--verbose\t\tVerbose."
      exit 1
      ;;
    *)
      # print error
      echo "Unknown argument: '$1'"
      exit 1
      ;;
  esac
  shift
done

if [ "X" = "X$TAG" ];
then
  echo "No tag specified. Unable to continue."
  exit 1
fi

if [ "X" = "X$BASE" ];
then
  echo "No base specified. Unable to continue."
  exit 1
fi

if [ "X" = "X$CLIENT_NAME" ];
then
  echo "No client name specified. Unable to continue."
  exit 1
fi

if [ "X" = "X$LICENSE_NAME" ];
then
  echo "No license name specified. Unable to continue."
  exit 1
fi

if [ "X" = "X$LICENSE_FOR" ];
then
  echo "No license for specified. Unable to continue."
  exit 1
fi

if [ ! -d "git_repos" ];
then
    [ $VERBOSE ] && echo "Cloning from source: $GIT_REPOS"
    git clone --bare $GIT_REPOS git_repos
fi

for branch in `(cd git_repos && git branch -a | grep remotes | grep -v HEAD | grep -v master)`;
do
    (cd git_repos && git branch --track ${branch##*/} $branch) 2>/dev/null
done
[ $VERBOSE ] && echo "Fetching from source: $GIT_REPOS"
(cd git_repos && git fetch origin && git fetch origin --tags)

echo ".gitignore export-ignore" > git_repos/.gitattributes
echo "*/.gitignore export-ignore" >> git_repos/.gitattributes

make_client_package()
{
    CLIENT_NAME=$1
    CLIENT_PRODUCTS=$2
    CLIENT_LICENSE=$3
    ONLY_FILES=$4
    ONLY_FILES_FILTER=""

    if [ -d "$TAG-${CLIENT_LICENSE}" ];
    then
        [ $VERBOSE ] && echo "Skipping '$TAG-${CLIENT_LICENSE}'. Already exists."
        return 1
    fi

    TMP="$( mktemp -d )"
    [ $VERBOSE ] && echo "Exporting from Git: '$PROJECT' with tag '$TAG' to '$TMP'"
    
    if [ "X" != "X$ONLY_FILES" ];
    then
      ONLY_FILES_FILTER=" -a '('";

      for i in $ONLY_FILES;
      do
        ONLY_FILES_FILTER="$ONLY_FILES_FILTER -path $TMP/$TAG/$i -o";
      done

      ONLY_FILES_FILTER="$ONLY_FILES_FILTER -name SUPERNOTEXISTINGFILENAME ')'";
    fi

    mkdir -p $TMP/$TAG
    (cd git_repos && git archive $TAG) | tar -x -C $TMP/$TAG || exit 1

    [ $VERBOSE ] && echo "Preparing ${CLIENT_NAME} - ${CLIENT_PRODUCTS} version of tree"
    $BASE/generate_license.pl "$BASE" "$TMP/$TAG/LICENSE" "$CLIENT_NAME" "$CLIENT_PRODUCTS" "$CLIENT_LICENSE" || exit 1
    CMD="find \"$TMP/$TAG\" '(' '!' -name fct.h -a '(' -name '*.c' -o -name '*.h' ')' $ONLY_FILES_FILTER ')' -print -exec \"$BASE/replace_copyright.pl\" {} \; || exit 1"
    eval "$CMD"
    CMD="find \"$TMP/$TAG\" '(' '!' -name fct.h -a '(' -name '*.c' -o -name '*.h' ')' $ONLY_FILES_FILTER ')' -print -exec \"$BASE/replace_license.pl\" \"$BASE\" {} \"$CLIENT_NAME\" \"$CLIENT_PRODUCTS\" \"$CLIENT_LICENSE\" \; || exit 1"
    eval "$CMD"
    tar zcf "$TAG-${CLIENT_LICENSE}.tgz" -C $TMP "$TAG" || exit 1
    rm -rf "$TMP/$TAG"
}

make_client_package "${CLIENT_NAME}" "${LICENSE_FOR}" "${LICENSE_NAME}" "${ONLY_FILES}"
