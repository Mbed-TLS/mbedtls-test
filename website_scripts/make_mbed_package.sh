#!/bin/bash

GIT_REPOS="git://github.com/ARMmbed/mbedtls"
PROJECT="mbedtls"
TAG=""
BASE=""

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
    -v|--verbose)
      # Be verbose
      VERBOSE="1"
      ;;
    -h|--help)
      # print help
      echo "Usage: $0"
      echo -e "  -b|--base\t\tBase dir of script root."
      echo -e "  -h|--help\t\tPrint this help."
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

make_mbed_package()
{
    if [ -d "$TAG-partner" ];
    then
        [ $VERBOSE ] && echo "Skipping '$TAG-partner'. Already exists."
        return 1
    fi

    TMP="$( mktemp -d )"
    [ $VERBOSE ] && echo "Exporting from Git: '$PROJECT' with tag '$TAG' to '$TMP'"

    mkdir -p $TMP/$TAG
    (cd git_repos && git archive $TAG) | tar -x -C $TMP/$TAG || exit 1

    [ $VERBOSE ] && echo "Preparing mbedtls partner version of tree"
    cp $BASE/license-mbed.txt "$TMP/$TAG/LICENSE" || exit 1
    cp $BASE/license-mbed.pdf "$TMP/$TAG/LICENSE.pdf" || exit 1

    CMD="find \"$TMP/$TAG\" \( ! -name fct.h -a \( -name '*.c' -o -name '*.cpp' -o -name '*.h' -o -name '*.fmt' \) \) -print -exec \"$BASE/apache_to_gpl.pl\" $BASE {} \; || exit 1"
    eval "$CMD"

    # Ensure the Apache license is stripped out
    [ -e $TMP/$TAG/apache-2.0.txt ] && rm "$TMP/$TAG/apache-2.0.txt"

    CMD="find \"$TMP/$TAG\" \( ! -name fct.h -a \( -name '*.c' -o -name '*.cpp' -o -name '*.h' -o -name '*.fmt' \) \) -print -exec \"$BASE/remove_license.pl\" {} \;  || exit 1"
    eval "$CMD"

    # The Yotta module is deprecated and does not have to be present in
    # partner license releases
    [ -e $TMP/$TAG/yotta ] && rm -r "$TMP/$TAG/yotta"

    tar zcf "$TAG-partner.tgz" -C $TMP "$TAG" || exit 1
    rm -rf "$TMP/$TAG"
}

make_mbed_package
