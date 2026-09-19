#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v411.sh
python tools/apply_v412_patch.py

echo "Football Stats Analyzer V4.1.2 source prepared"
