#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 lmcode/check-pins.py
./gradlew :lucene:core:jar :lucene:analysis:common:jar :lucene:queryparser:jar -Pgradle.ge=false --console=plain
mkdir -p lmcode/build/classes lmcode/build/lib
for module in core analysis/common queries sandbox queryparser facet; do
  cp lucene/"$module"/build/libs/lucene-*.jar lmcode/build/lib/
done
find lmcode/src lmcode/vendor/lmcode-java/src/main/java -name '*.java' | sort > lmcode/build/sources.txt
javac --release 21 -encoding UTF-8 -cp 'lmcode/build/lib/*' -d lmcode/build/classes @lmcode/build/sources.txt
jar --create --file lmcode/build/lmlucene.jar --main-class io.lmctl.lucene.Main -C lmcode/build/classes .
lmcode/lmlucene --dsl-schema --json > lmcode/build/lucene.dsl.json
echo 'Built lmcode/build/lmlucene.jar (Lucene compiled from this checkout)'
