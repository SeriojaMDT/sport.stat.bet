from pathlib import Path
import base64, zlib, subprocess, re

patch = zlib.decompress(base64.b64decode(Path("v410-patch.b64z").read_text().strip()))
subprocess.run(["patch", "-p0"], input=patch, check=True)

java = Path("app/src/main/java/com/serghei/footballpredictions/MainActivity.java")
js = java.read_text()
dup = '            case"Comparație":en="Comparison";break;\n'
first = js.find(dup)
second = js.find(dup, first + len(dup)) if first >= 0 else -1
if second >= 0:
    js = js[:second] + js[second + len(dup):]
java.write_text(js)

g = Path("app/build.gradle")
s = g.read_text()
s = re.sub(r"versionCode \d+", "versionCode 228", s, count=1)
s = re.sub(r"versionName '[^']*'", "versionName '1.2.0-test'", s, count=1)
g.write_text(s)
print("V4.1.0 patch applied")
