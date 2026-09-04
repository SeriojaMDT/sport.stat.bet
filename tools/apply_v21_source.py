from pathlib import Path
import base64
import zlib

parts = [Path(f"v21-source.b64z.part{i}").read_text(encoding="utf-8").strip() for i in range(1, 7)]
data = "".join(parts)
target = Path("app/src/main/java/com/serghei/footballpredictions/MainActivity.java")
target.write_bytes(zlib.decompress(base64.b64decode(data)))
print("BestSportStats V2.1 source applied", target.stat().st_size)
