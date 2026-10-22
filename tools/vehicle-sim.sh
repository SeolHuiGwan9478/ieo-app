#!/usr/bin/env bash
# Pleos 에뮬레이터의 차량 신호(기어·속도)와 GPS 위치를 바꾸는 스크립트.
# 개발 중 테스트와 시연 영상 녹화에 쓴다. 근거: research/03_기술검증.md
#
# 사용법
#   tools/vehicle-sim.sh gear Drive          # Park | Reverse | Neutral | Drive
#   tools/vehicle-sim.sh speed 60            # km/h (0~240)
#   tools/vehicle-sim.sh geo 36.9922 127.0026  # 위도 경도
#   tools/vehicle-sim.sh stop                # 정차: 속도 0, 기어 P
#   tools/vehicle-sim.sh drive 80            # 출발: 기어 D, 속도 지정
#   tools/vehicle-sim.sh state               # 현재 기어·속도
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
CONSOLE="http://127.0.0.1:8088"

# Virtual Console은 브라우저 세션이 1초마다 살아 있다고 알릴 때만 신호 변경을 받는다.
# 명령을 보내기 전후로 잠깐 세션을 유지해 준다.
ensure_console() {
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  for _ in 1 2 3; do
    curl -s -m 2 -X POST "$CONSOLE/browser/session?client=ieo-sim" >/dev/null || true
    sleep 1
  done
  (for _ in $(seq 1 5); do curl -s -m 2 -X POST "$CONSOLE/browser/session?client=ieo-sim" >/dev/null || true; sleep 1; done) &
}

post() {
  local result
  result=$(curl -s -m 5 -X POST "$CONSOLE$1")
  if [[ "$result" == *'"error"'* ]]; then
    echo "실패: $result" >&2
    exit 1
  fi
}

case "${1:-}" in
  gear)  ensure_console; post "/vehicle/gear?gear=$2"; echo "기어 $2" ;;
  speed) ensure_console; post "/vehicle/controls?speed_kph=$2"; echo "속도 $2 km/h" ;;
  geo)   "$ADB" emu geo fix "$3" "$2" >/dev/null; echo "위치 $2, $3" ;;
  stop)  ensure_console; post "/vehicle/controls?speed_kph=0"; post "/vehicle/gear?gear=Park"; echo "정차 (P, 0 km/h)" ;;
  drive) ensure_console; post "/vehicle/gear?gear=Drive"; post "/vehicle/controls?speed_kph=${2:-60}"; echo "주행 (D, ${2:-60} km/h)" ;;
  state)
    "$ADB" forward tcp:8088 tcp:8088 >/dev/null
    curl -s -m 5 "$CONSOLE/vehicle/state" | python3 -c '
import json, sys
d = json.load(sys.stdin)
gear = (d["gear"].get("actual") or {}).get("value")
print("기어", gear, "/ 속도", d["controls"]["speed_kph"], "km/h")'
    ;;
  *) sed -n '2,13p' "$0"; exit 1 ;;
esac
