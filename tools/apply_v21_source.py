from pathlib import Path
import base64
import zlib

parts = [Path(f"v21-source.b64z.part{i}").read_text(encoding="utf-8").strip() for i in range(1, 6)]
parts += [
    Path("v21-source.b64z.part6a").read_text(encoding="utf-8").strip(),
    Path("v21-source.b64z.part6b").read_text(encoding="utf-8").strip(),
]
data = "".join(parts)
target = Path("app/src/main/java/com/serghei/footballpredictions/MainActivity.java")
raw = zlib.decompress(base64.b64decode(data))
target.write_bytes(raw)
print("BestSportStats V2.1 source applied", len(raw))
