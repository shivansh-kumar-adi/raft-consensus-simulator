#!/bin/sh
# Usage: ./run.sh [options]     e.g.  ./run.sh   |  ./run.sh --cli  |  ./run.sh --demo
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
java -cp "out:lib/*" raft.Main "$@"
