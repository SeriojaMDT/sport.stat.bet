#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v410.sh
python tools/apply_v411_patch.py

echo "Football Stats Analyzer V4.1.1 source prepared"
