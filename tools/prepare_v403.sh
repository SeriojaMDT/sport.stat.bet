#!/usr/bin/env bash
set -euo pipefail

python tools/apply_v21_source.py
python -c "import base64,zlib; exec(zlib.decompress(base64.b64decode(open('v23-patch.b64z').read())))"

python -c "import base64,zlib; open('/tmp/v24.patch','wb').write(zlib.decompress(base64.b64decode(open('v24-patch.b64z').read())))"
patch -p0 < /tmp/v24.patch
python -c "import base64,zlib; open('/tmp/v25.patch','wb').write(zlib.decompress(base64.b64decode(open('v25-patch.b64z').read())))"
patch -p0 < /tmp/v25.patch
python -c "import base64,zlib; open('/tmp/v26.patch','wb').write(zlib.decompress(base64.b64decode(open('v26-patch.b64z').read())))"
patch -p0 < /tmp/v26.patch
python -c "import base64,zlib; open('/tmp/v27.patch','wb').write(zlib.decompress(base64.b64decode(open('v27-patch.b64z').read())))"
patch -p0 < /tmp/v27.patch

cat v28-patch.b64z.part1 v28-patch.b64z.part2 v28-patch.b64z.part3 v28-patch.b64z.part4 v28-patch.b64z.part5 v28-patch.b64z.part6 v28-patch.b64z.part7 > /tmp/v28.b64
python -c "import base64,zlib; open('/tmp/v28.patch','wb').write(zlib.decompress(base64.b64decode(open('/tmp/v28.b64').read())))"
patch -p0 < /tmp/v28.patch

cat v29-patch.b64z.part1 v29-patch.b64z.part2 v29-patch.b64z.part3 > /tmp/v29.b64
python -c "import base64,zlib; open('/tmp/v29.patch','wb').write(zlib.decompress(base64.b64decode(open('/tmp/v29.b64').read())))"
sed -i '1c--- app/src/main/java/com/serghei/footballpredictions/MainActivity.java' /tmp/v29.patch
sed -i '2c+++ app/src/main/java/com/serghei/footballpredictions/MainActivity.java' /tmp/v29.patch
patch -p0 < /tmp/v29.patch
sed -i 's/if("goals".equals(module))return half?.35:.60;/if("goals".equals(module))return half?.35:.45;/' app/src/main/java/com/serghei/footballpredictions/MainActivity.java

python -c "import base64,zlib; open('/tmp/v30.patch','wb').write(zlib.decompress(base64.b64decode(open('v30-patch.b64z').read())))"
patch -p0 < /tmp/v30.patch
cat v31.patch.part00 v31.patch.part01 v31.patch.part02 v31.patch.part03 v31.patch.part04 v31.patch.part05 v31.patch.part06 v31.patch.part07 v31.patch.part08 > /tmp/v31.patch
patch -p0 < /tmp/v31.patch
python -c "import base64,zlib; open('/tmp/v32.patch','wb').write(zlib.decompress(base64.b64decode(open('v32-patch.b64z').read())))"
patch -p0 < /tmp/v32.patch
python -c "import base64,zlib; open('/tmp/v33.patch','wb').write(zlib.decompress(base64.b64decode(open('v33-patch.b64z').read())))"
patch -p0 < /tmp/v33.patch

python -c "import base64,zlib; open('/tmp/v34.patch','wb').write(zlib.decompress(base64.b64decode(open('v34-patch.b64z').read())))"
sed -i '1c--- app/src/main/java/com/serghei/footballpredictions/MainActivity.java' /tmp/v34.patch
sed -i '2c+++ app/src/main/java/com/serghei/footballpredictions/MainActivity.java' /tmp/v34.patch
patch -p0 < /tmp/v34.patch

cat v35-patch.b64z.part1 v35-patch.b64z.part2 v35-patch.b64z.part3 v35-patch.b64z.part4 > /tmp/v35.b64
python -c "import base64,zlib; open('/tmp/v35.patch','wb').write(zlib.decompress(base64.b64decode(open('/tmp/v35.b64').read())))"
patch -p0 < /tmp/v35.patch
python -c "import base64,zlib; open('/tmp/v36.patch','wb').write(zlib.decompress(base64.b64decode(open('v36-patch.b64z').read())))"
patch -p0 < /tmp/v36.patch
cat v37-patch.b64z.part1 v37-patch.b64z.part2 v37-patch.b64z.part3 v37-patch.b64z.part4 > /tmp/v37.b64
python -c "import base64,zlib; open('/tmp/v37.patch','wb').write(zlib.decompress(base64.b64decode(open('/tmp/v37.b64').read())))"
patch -p0 < /tmp/v37.patch
cat v38-patch.b64z.part1 v38-patch.b64z.part2 v38-patch.b64z.part3 v38-patch.b64z.part4 > /tmp/v38.b64
python -c "import base64,zlib; open('/tmp/v38.patch','wb').write(zlib.decompress(base64.b64decode(open('/tmp/v38.b64').read())))"
patch -p0 < /tmp/v38.patch
python -c "import base64,zlib; open('/tmp/v39.patch','wb').write(zlib.decompress(base64.b64decode(open('v39-patch.b64z').read())))"
patch -p0 < /tmp/v39.patch

curl -fsSL https://raw.githubusercontent.com/SeriojaMDT/sport.stat.bet/fff6775c9b2bc3b7647ec7408f2840d4daac48c5/v40-patch.b64z -o /tmp/v40-good.b64z
python -c "import base64,zlib; open('/tmp/v40.patch','wb').write(zlib.decompress(base64.b64decode(open('/tmp/v40-good.b64z').read())))"
patch -p0 < /tmp/v40.patch

python tools/apply_v402_changes.py
python tools/apply_v403_changes.py

sed -i "s/namespace 'com.serghei.footballpredictions'/namespace 'com.turcanapps.bestfootballstats'/" app/build.gradle
sed -i "s/applicationId 'com.serghei.footballpredictions'/applicationId 'com.turcanapps.bestfootballstats'/" app/build.gradle
sed -i "s/versionCode [0-9][0-9]*/versionCode 221/" app/build.gradle
sed -i "s/versionName '[^']*'/versionName '1.1.3-test'/" app/build.gradle
sed -i 's/android:label="BestSportStats"/android:label="Football Stats Analyzer"/' app/src/main/AndroidManifest.xml
sed -i 's/android:name="\.MainActivity"/android:name="com.turcanapps.bestfootballstats.MainActivity"/' app/src/main/AndroidManifest.xml

echo "Football Stats Analyzer V4.0.3 source prepared"
