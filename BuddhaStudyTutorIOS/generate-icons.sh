#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/AppIconSource.jpg"
OUT="$ROOT/BuddhaStudyTutorIOS/Assets.xcassets/AppIcon.appiconset"
mkdir -p "$OUT"
make_icon() {
  local px="$1" file="$2"
  sips -s format png -z "$px" "$px" "$SRC" --out "$OUT/$file" >/dev/null
}
make_icon 40  'Icon-20@2x.png'
make_icon 60  'Icon-20@3x.png'
make_icon 58  'Icon-29@2x.png'
make_icon 87  'Icon-29@3x.png'
make_icon 80  'Icon-40@2x.png'
make_icon 120 'Icon-40@3x.png'
make_icon 120 'Icon-60@2x.png'
make_icon 180 'Icon-60@3x.png'
make_icon 1024 'Icon-1024.png'
echo "Generated BuddhaStudy Tutor iPhone icons."
