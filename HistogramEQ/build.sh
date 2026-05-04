#!/usr/bin/env bash
set -e
mkdir -p out
javac -d out src/*.java src/benchmark/*.java src/services/*.java
echo "Build OK"
java -cp out Main "$@"
