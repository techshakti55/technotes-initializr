#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes
javac --release 21 -encoding UTF-8 -d build/classes src/LocalInitializr.java
jar --create --file technotes-initializr-v4.jar --main-class LocalInitializr -C build/classes . -C resources .
echo 'Built technotes-initializr-v4.jar'
