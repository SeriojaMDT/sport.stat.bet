from pathlib import Path
import base64
import hashlib
import zlib

parts = sorted(Path('.').glob('v22-source.b64z.part*'))
if not parts:
    raise RuntimeError('Missing BestSportStats V2.2 source chunks')

data = ''.join(p.read_text(encoding='utf-8').strip() for p in parts)
raw = zlib.decompress(base64.b64decode(data))
expected = 'ea6ef46e53968c4a53549b074e0d3ee06f90fad2581e0bc579767c1d2fa8b358'
actual = hashlib.sha256(raw).hexdigest()
if actual != expected:
    raise RuntimeError(f'V2.2 source checksum mismatch: {actual}')

target = Path('app/src/main/java/com/serghei/footballpredictions/MainActivity.java')
target.write_bytes(raw)
print('BestSportStats V2.2 source applied', len(raw), actual)
