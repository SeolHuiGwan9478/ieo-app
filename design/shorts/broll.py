#!/usr/bin/env python3
"""
숏폼에 섞을 분위기 컷(B-roll)을 OpenRouter 영상 모델로 만든다.

  python3 design/shorts/broll.py wait          # 컷 하나
  python3 design/shorts/broll.py wait rest     # 여러 개
  python3 design/shorts/broll.py --list        # 컷 목록과 예상 비용
  python3 design/shorts/broll.py --get wait <작업 id>  # 다 만든 것을 다시 받기만

결과: ~/Desktop/ieo-shorts/broll/<이름>.mp4 (세로 1080x1920, 현장 소리 포함)
키: ieo-server/.env 의 OPENROUTER_API_KEY 를 읽는다. 키는 화면에 찍지 않는다.
앱 화면은 만들지 않는다(글자가 뭉개진다). 상표·로고·글자가 없는 분위기 장면만.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

MODEL = "google/veo-3.1-lite"
PRICE_PER_SEC = 0.08  # 1080p, 소리 포함 (2026-11-14 /videos/models)
OUT = Path.home() / "Desktop" / "ieo-shorts" / "broll"
ENV = Path.home() / "Desktop" / "ieo-server" / ".env"
API = "https://openrouter.ai/api/v1"

STYLE = ("Vertical 9:16 documentary-style footage, natural colors, realistic, shallow depth of field, "
         "no text, no logos, no brand names, no readable signs, no license plates. "
         "The trucks are very large heavy-duty Korean cab-over freight trucks (25-ton class, long cargo beds and "
         "tractor-trailers), not small vans. Natural ambient sound only, no music, no speech. ")

CUTS = {
    # 1번 장면 바탕: 상하차지에서 줄 서 기다리는 대형 트럭
    "wait": dict(sec=4, prompt=STYLE + "Late afternoon at a huge logistics warehouse loading dock. A long line of "
                 "heavy-duty 25-ton cargo trucks waits in a queue with engines idling. Low angle: a tired middle-aged "
                 "driver leans out of the high cab window and checks his wristwatch. Slow push-in camera move. "
                 "Sound: idling diesel engines, distant forklift beeps."),
    # 휴게 장면 앞: 밤 고속도로를 달리는 대형 트럭 운전석
    "drive": dict(sec=4, prompt=STYLE + "Night highway seen from inside the high cab of a heavy-duty truck, "
                  "dashboard glow, the driver's hands on a large steering wheel, streetlights passing, a center "
                  "touchscreen on the dashboard glows softly with abstract shapes. Gentle motion. "
                  "Sound: low diesel engine hum, tires on the road."),
    # 휴게 장면 뒤: 새벽 휴게소에 줄지어 선 대형 트럭, 기지개
    "rest": dict(sec=4, prompt=STYLE + "Early morning at a highway truck rest area, a row of huge tractor-trailers "
                 "and 25-ton cargo trucks parked side by side; a driver climbs down the steps of the tall cab, "
                 "stretches his back and holds a paper cup of coffee, soft sunrise light. Slow lateral camera move. "
                 "Sound: birds, distant highway traffic."),
    # 다음 일 장면 앞: 짐을 다 내리고 다시 출발하는 대형 트럭
    "next": dict(sec=4, prompt=STYLE + "A forklift lifts the last pallet off the long flatbed of a heavy-duty "
                 "25-ton cargo truck at a warehouse; the driver closes the side gates, climbs into the high cab and "
                 "the big truck pulls away toward the open road at golden hour. Wide shot, steady camera. "
                 "Sound: forklift, air brakes release, engine starting."),
    # B판 문제 장면: 밤 운전석에서 영수증을 손으로 정리하는 기사
    "paper": dict(sec=4, prompt=STYLE + "Night, inside the high cab of a parked heavy-duty truck, dim dome light. "
                  "A tired middle-aged driver sorts a messy pile of crumpled paper receipts on the steering wheel and "
                  "writes in a worn notebook with a pen, rubbing his eyes. Close-up, handheld, warm low light. "
                  "Sound: quiet cab, paper rustling, pen scratching, distant highway."),
    # B판 끝 장면 바탕: 해 질 녘 고속도로를 줄지어 달리는 대형 트럭 항공샷
    "aerial": dict(sec=4, prompt=STYLE + "Aerial drone shot at golden hour, slowly tracking a convoy of huge "
                   "heavy-duty 25-ton cargo trucks and tractor-trailers driving along a wide Korean highway through "
                   "green hills, long shadows, cinematic, smooth forward camera move. "
                   "Sound: wind, distant engines."),
}


def key():
    for line in ENV.read_text(encoding="utf-8").splitlines():
        if line.startswith("OPENROUTER_API_KEY="):
            return line.split("=", 1)[1].strip().strip('"')
    sys.exit("ieo-server/.env 에 OPENROUTER_API_KEY 가 없어요")


def call(method, url, body=None, raw=False):
    req = urllib.request.Request(url if url.startswith("http") else API + url, method=method,
                                 headers={"Authorization": "Bearer " + key(), "Content-Type": "application/json"},
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=120) as res:
            data = res.read()
            return data if raw else json.loads(data)
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {url}: HTTP {e.code} {e.read().decode()[:500]}")


def make(name):
    cut = CUTS[name]
    OUT.mkdir(parents=True, exist_ok=True)
    job = call("POST", "/videos", dict(model=MODEL, prompt=cut["prompt"], duration=cut["sec"], resolution="1080p",
                                        aspect_ratio="9:16", generate_audio=True))
    print(f"{name}: 요청함 ({job.get('id')}), 약 ${cut['sec'] * PRICE_PER_SEC:.2f}", flush=True)
    status = job
    for _ in range(80):
        if status.get("status") == "completed":
            break
        if status.get("status") in ("failed", "cancelled", "expired"):
            sys.exit(f"{name}: {status.get('status')} {status.get('error')}")
        time.sleep(15)
        status = call("GET", status.get("polling_url") or f"/videos/{job['id']}")
    else:
        sys.exit(f"{name}: 20분 안에 끝나지 않았어요")
    download(name, job["id"])


def download(name, job_id):
    """다 만든 영상을 받는다. 서명 없는 주소는 인증이 필요해서 키를 붙여 content로 받는다"""
    data = call("GET", f"/videos/{job_id}/content?index=0", raw=True)
    path = OUT / f"{name}.mp4"
    path.write_bytes(data)
    print(f"{name}: {path} ({len(data) // 1024}KB)", flush=True)


def main():
    args = sys.argv[1:]
    # 이미 만든 작업을 다시 요청하지 않고 받기만: broll.py --get <컷 이름> <작업 id>
    if args[:1] == ["--get"] and len(args) == 3:
        OUT.mkdir(parents=True, exist_ok=True)
        download(args[1], args[2])
        return
    if not args or args == ["--list"]:
        for n, c in CUTS.items():
            print(f"{n:6s} {c['sec']}초  약 ${c['sec'] * PRICE_PER_SEC:.2f}")
        return
    for name in args:
        if name not in CUTS:
            sys.exit(f"모르는 컷: {name} (있는 것: {', '.join(CUTS)})")
    for name in args:
        make(name)


if __name__ == "__main__":
    main()
