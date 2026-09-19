#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v409.sh
python tools/apply_v410_patch.py

echo "Football Stats Analyzer V4.1.0 source prepared"
