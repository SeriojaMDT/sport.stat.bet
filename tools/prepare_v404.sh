#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v403.sh
python -c "import base64,zlib; open('/tmp/v404.patch','wb').write(zlib.decompress(base64.b64decode(open('v404-patch.b64z').read())))"
patch -p0 < /tmp/v404.patch

sed -i "s/versionCode [0-9][0-9]*/versionCode 222/" app/build.gradle
sed -i "s/versionName '[^']*'/versionName '1.1.4-test'/" app/build.gradle

echo "Football Stats Analyzer V4.0.4 source prepared"
