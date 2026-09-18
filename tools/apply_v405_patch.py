from pathlib import Path
import base64, zlib, subprocess

patch = zlib.decompress(base64.b64decode(Path("v405-patch.b64z").read_text().strip()))
subprocess.run(["patch", "-p0"], input=patch, check=True)

g = Path("app/build.gradle")
s = g.read_text()
import re
s = re.sub(r"versionCode \d+", "versionCode 223", s, count=1)
s = re.sub(r"versionName '[^']*'", "versionName '1.1.5-test'", s, count=1)
g.write_text(s)
print("V4.0.5 patch applied")
