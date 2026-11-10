#!/bin/sh
# guide.html을 A4 가로 PDF로 만든다 (Chrome 필요).
#   sh docs/service-guide/make-pdf.sh
set -e
cd "$(dirname "$0")"
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --no-pdf-header-footer \
  --virtual-time-budget=8000 --print-to-pdf="$PWD/이에오_서비스_설명서.pdf" "file://$PWD/guide.html" 2>/dev/null
echo "만들었어요: $PWD/이에오_서비스_설명서.pdf"
