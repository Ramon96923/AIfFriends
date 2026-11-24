#!/bin/bash
# Dumps public signatures (javap) of selected Minecraft classes so the mod can be kept in sync with new versions.
[ -f .github/apidump.txt ] || exit 0
mkdir -p apidump
JARS=$(find ~/.gradle .gradle -name '*.jar' 2>/dev/null | grep -iE 'minecraft' | grep -viE 'sources|fabric-api|loader|mapping' | sort -u)
echo "$JARS" > apidump/_jars.txt
CP=$(echo "$JARS" | tr '\n' ':')
for j in $JARS; do unzip -Z1 "$j" 2>/dev/null; done | grep '\.class$' | sort -u > apidump/_classes.txt
while read -r name; do
  [ -z "$name" ] && continue
  grep -E "(^|/)${name}\.class$" apidump/_classes.txt | while read -r path; do
    cls=${path%.class}; cls=${cls//\//.}
    javap -p -cp "$CP" "$cls" > "apidump/${cls}.txt" 2>&1
  done
done < .github/apidump.txt
