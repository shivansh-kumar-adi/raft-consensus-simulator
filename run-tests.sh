#!/bin/sh
# Runs the automated test suite (takes about 30 seconds)
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
java -cp "out:lib/*" raft.RaftTests
