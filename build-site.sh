#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh build.sh
javac --release 21 -encoding UTF-8 -cp build/classes -d build/classes src/ExportBrowser.java
java -cp build/classes:resources ExportBrowser site
