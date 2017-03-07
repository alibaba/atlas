#!/bin/bash
# Run with no arguments from any directory, with no special setup required.

# Abort if any command returns an error exit status, or if an undefined
# variable is used.
set -e
set -u

base_dir=$(realpath $(dirname $0))

# Extract the latest version from the web page.
new_version=$(wget -O - --no-verbose -q http://zlib.net/ | \
              grep 'http://zlib.net/zlib-[0-9].*.tar.gz' | \
              sed 's/.*zlib-\(.*\)\.tar\.gz.*/\1/')
tgz_file="zlib-$new_version.tar.gz"

echo "Upgrading zlib to version $new_version..."
echo "-------------------------------------------------------------------"

echo "Downloading $tgz_file..."
wget -O /tmp/$tgz_file --no-verbose "http://zlib.net/$tgz_file"

echo "Cleaning out old version..."
src_dir=$base_dir/src
rm -rf $src_dir

echo "Unpacking new version..."
cd $base_dir
tar zxf /tmp/$tgz_file
mv zlib-$new_version src

echo "Configuring new version..."
cd src
./configure
rm Makefile configure.log
cd ..

echo "Fixing NOTICE file..."
grep -A21 'Copyright notice:' src/README | tail -20 > NOTICE

md5_sum=$(md5sum /tmp/$tgz_file)
echo "MD5: $md5_sum"
