#!/bin/sh
# Compiles the whole project into ./out  (needs a JDK: javac)
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
find src test -name "*.java" > sources.txt
javac -d out @sources.txt
rm -f sources.txt
echo "Build OK  ->  ./out"
