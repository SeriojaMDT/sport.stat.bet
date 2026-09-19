from pathlib import Path
import base64, zlib, subprocess, re

patch = zlib.decompress(base64.b64decode(Path("v412-patch.b64z").read_text().strip()))
subprocess.run(["patch", "-p0"], input=patch, check=True)

g = Path("app/build.gradle")
s = g.read_text()
s = re.sub(r"versionCode \d+", "versionCode 230", s, count=1)
s = re.sub(r"versionName '[^']*'", "versionName '1.2.2-test'", s, count=1)
g.write_text(s)
print("V4.1.2 patch applied")
