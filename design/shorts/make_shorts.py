#!/usr/bin/env python3
"""
이에오 숏폼(세로 1080x1920, 1분 안쪽) 만들기. 기사님 한 사람의 하루(상차지 → 밤길 → 하차지)로 이어진다.

  python3 design/shorts/make_shorts.py      # A판: 하루 이야기, 차분하게 (약 49초)
  python3 design/shorts/make_shorts.py b    # B판: 빠르게, 기능을 번호로, 자막·줌·밝은 음악 (약 45초)

결과: ~/Desktop/ieo-shorts/ (B판은 파일 이름 끝에 -b)
- ieo-shorts.mp4          내레이션 + 배경음악 + 현장 소리
- ieo-shorts-nonarr.mp4   내레이션만 뺀 판 (직접 녹음한 목소리를 얹을 때)
- scenes/NN.png           장면마다 영상 위에 얹는 글자·앱 화면 (바탕 투명)
- narration.txt           장면별 내레이션과 시작 시각

재료 (없으면 만든다. 이미 있으면 다시 부르지 않는다)
- broll/<이름>.mp4        실사 컷. python3 design/shorts/broll.py <이름>
- audio/voice/<해시>.wav  내레이션 한 줄씩. OpenRouter TTS (Gemini, 남성 차분)
- audio/music.mp3         배경음악. OpenRouter Lyria (B판은 music-b.mp3)
키는 ieo-server/.env 의 OPENROUTER_API_KEY 를 읽고 화면에 찍지 않는다.
앱 화면은 docs/service-guide/assets 의 실제 캡처를 쓴다.
"""
import base64
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "docs" / "service-guide" / "assets"
OUT = Path.home() / "Desktop" / "ieo-shorts"
ENV = Path.home() / "Desktop" / "ieo-server" / ".env"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
W, H, FPS = 1080, 1920, 30
FADE = 0.4          # 장면 사이 겹침
GAP = 0.25          # 내레이션 줄 사이 숨
LEAD = 0.5          # 영상 시작 뒤 첫 말까지
MIN_UI = 3.2        # 앱 화면 장면은 읽을 시간을 이만큼은 준다
MIN_ANY = 2.6

TTS_MODEL = "google/gemini-3.8-flash-tts"
TTS_VOICE = "Charon"
TTS_STYLE = "따뜻하고 차분한 30~40대 남성 내레이터. 다큐멘터리처럼 자연스럽게, 너무 빠르지 않게. 문장 끝을 올리지 말고 담담하게."
MUSIC_MODEL = "google/lyria-3-pro-preview"
MUSIC_PROMPT = ("Instrumental only, no vocals, about 60 seconds. Warm, hopeful, cinematic acoustic background music "
                "for a short documentary about a long-haul truck driver's day: starts calm and slightly tired at dusk, "
                "gently builds with soft piano, acoustic guitar and light pulse, ends uplifting and resolved. "
                "Moderate tempo around 90 BPM, unobtrusive under voice-over.")

DRIVE_BLUR = [(470, 1320, 140, 120), (490, 1490, 130, 50)]
NEXT_BLUR = [(500, 1010, 140, 70), (380, 1220, 130, 70)]

# 장면. bg: 바탕 실사 컷 이름(broll/), sharp: 바탕을 선명하게(실사가 주인공), 아니면 흐리게 깔고 앱 화면을 띄운다
# say: 내레이션 한 줄(없으면 hold초 동안 소리·음악만), shot: 앱 화면 캡처, box/pos: 캡처의 어느 부분을 보일지
# blur: 영상 모델이 지어낸 상표 글자·실제 회사처럼 보이는 로고를 흐릴 자리 (x, y, 가로, 세로)
SCENES_A = [
    dict(bg="wait", sharp=True, kind="hook", blur=[(150, 1235, 280, 130)],
         title="2시간 기다렸는데<br><em>대기비는 0원?</em>",
         sub="화물차 기사 64%가 상하차지에서 1시간 넘게 기다려요<br><small>상용차매거진 2025.4, 기사 281명 설문</small>",
         say="상하차지에서 두 시간을 기다려도, 대기비는 못 받을 때가 많죠."),
    dict(bg="wait", step="도착", title="세우면 차가<br><em>먼저 물어요</em>", shot="c_wait_confirm.jpg",
         say="이제는 차가 먼저 알아챕니다. 세우면, 도착하셨어요? 하고 묻고,"),
    dict(bg="wait", step="대기", title="멈춘 순간부터<br><em>대기비</em>가 쌓여요", shot="c_wait_waiting.jpg",
         say="멈춘 순간부터 대기 시간과 대기비를 재 줘요."),
    dict(bg="wait", step="증빙", title="대기 증빙은<br><em>링크 하나</em>로", shot="c_wait_proof_sheet.jpg",
         box=(880, 700), pos="bottom", say="떠날 땐 증빙 링크 하나로 주선사에게 보내면 끝."),
    dict(bg="drive", sharp=True, kind="caption", blur=DRIVE_BLUR,
         title="다시 밤길,<br>2시간째", say="다시 밤길. 두 시간을 내리 달리면,"),
    dict(bg="drive", step="휴게", title="2시간 되기 전에<br><em>쉴 곳</em>까지", shot="c_rest_soon.jpg",
         blur=DRIVE_BLUR, say="쉬어 갈 휴게소를 미리 알려 주고,"),
    dict(bg="drive", step="주행 중", title="달리는 동안엔<br><em>말로만</em>", shot="c_trip_driving.jpg",
         box=(840, 860), pos="center", blur=DRIVE_BLUR, say="달리는 동안엔 화면 대신 목소리로 챙겨 줘요."),
    dict(bg="rest", sharp=True, kind="none", hold=2.6),
    dict(bg="next", sharp=True, kind="caption", blur=NEXT_BLUR, title="짐을 다 내리면", say="짐을 다 내리면,"),
    dict(bg="next", step="장부", title="오늘 운행은<br>장부에 <em>저절로</em>", shot="c_ledger_entries.jpg",
         blur=NEXT_BLUR, say="오늘 운행은 장부에 저절로 적히고,"),
    dict(bg="next", step="다음 일", title="그 자리에서<br><em>다음 일</em>까지", shot="c_pick_modal.jpg",
         blur=NEXT_BLUR, say="그 자리에서 다음 일까지 이어 줍니다."),
    dict(kind="outro", title="끝나는 곳에서,<br>다음이 이어집니다", sub="Pleos Connect 차량 앱 · 휴대폰 앱 · 주선사 콘솔",
         say="끝나는 곳에서, 다음이 이어집니다. 이에오.", tail=1.2),
]

# B판: 빠르게. 아픈 곳 두 컷 → "이제는 차가" → 차량 화면 → 기능 넷을 번호로 → 차·휴대폰 → 항공샷 끝. 자막을 깔고 화면이 천천히 다가간다
SCENES_B = [
    dict(bg="wait", sharp=True, kind="hook", blur=[(150, 1235, 280, 130)],
         title="기사님, 오늘<br><em>몇 시간</em><br>기다리셨어요?",
         say="기사님, 오늘 상차지에서 몇 시간 기다리셨어요?"),
    dict(bg="paper", sharp=True, kind="caption", title="대기비는 못 받고<br>장부는 <em>밤에 따로</em>",
         say="대기비는 못 받고, 영수증은 밤에 따로 정리하고."),
    dict(kind="outro", title="이제는<br><em>차가</em> 챙겨요", say="이제는 차가 챙깁니다."),
    dict(bg="drive", step="Pleos Connect", title="내비 옆에<br><em>그대로</em>", shot="r_car_split.jpg",
         box=(1000, 760), pos="center", blur=DRIVE_BLUR, say="운전석 화면, 내비 바로 옆에서요."),
    dict(bg="wait", step="① 대기비", title="세우면 차가<br><em>먼저</em> 물어요", shot="c_wait_confirm.jpg",
         say="세우면 차가 먼저, 도착하셨어요? 하고 묻고,"),
    dict(bg="wait", step="① 대기비", title="멈추면<br>대기비가 <em>쌓여요</em>", shot="c_wait_waiting.jpg",
         say="멈춘 순간부터 대기 시간과 대기비가 쌓이고,"),
    dict(bg="wait", step="① 대기비", title="증빙은<br><em>링크 하나</em>로", shot="c_wait_proof_sheet.jpg",
         box=(880, 700), pos="bottom", say="증빙은 링크 하나로 보내요."),
    dict(bg="drive", step="② 휴게", title="쉴 곳은<br><em>미리</em> 알려요", shot="c_rest_soon.jpg",
         blur=DRIVE_BLUR, say="두 시간 달리면, 쉴 곳을 먼저 알려 주고,"),
    dict(bg="drive", step="② 휴게", title="달릴 땐<br><em>말로만</em>", shot="c_trip_driving.jpg",
         box=(840, 860), pos="center", blur=DRIVE_BLUR, say="달리는 동안엔 화면 대신 목소리로 챙겨요."),
    dict(bg="next", step="③ 장부", title="장부는<br><em>저절로</em>", shot="c_ledger_entries.jpg",
         blur=NEXT_BLUR, say="운행이 끝나면 장부는 저절로 적히고,"),
    dict(bg="next", step="③ 장부", title="못 받은 돈도<br><em>놓치지 않게</em>", shot="c_ledger_owed.jpg",
         blur=NEXT_BLUR, say="못 받은 운임은 놓치지 않게 짚어 주고,"),
    dict(bg="next", step="③ 장부", title="세금계산서도<br><em>바로</em>", shot="m_TX_issued.jpg",
         blur=NEXT_BLUR, say="세금계산서도 휴대폰에서 바로 끊어요."),
    dict(bg="next", step="④ 다음 일", title="그 자리에서<br><em>다음 일</em>까지", shot="c_pick_modal.jpg",
         blur=NEXT_BLUR, say="그리고 그 자리에서, 다음 일까지 이어 줘요."),
    dict(bg="aerial", step="차 · 휴대폰", title="차에서도<br>휴대폰에서도 <em>이어서</em>", shot="m_HOME_tiles.jpg",
         say="차에서도, 휴대폰에서도 똑같이 이어집니다."),
    dict(bg="aerial", sharp=True, kind="outro", title="끝나는 곳에서,<br>다음이 이어집니다",
         sub="Pleos Connect 차량 앱 · 휴대폰 앱 · 주선사 콘솔",
         say="끝나는 곳에서, 다음이 이어집니다. 이에오.", tail=1.0),
]

# 판마다 다른 것: 장면, 말투, 음악, 자막·줌·넘김
VERSIONS = {
    "a": dict(scenes=SCENES_A, suffix="", style=TTS_STYLE, music_prompt=MUSIC_PROMPT, music="music.mp3",
              subs=False, zoom=False, transition="fade", fade=FADE, gap=GAP),
    "b": dict(scenes=SCENES_B, suffix="-b", music="music-b.mp3", subs=True, zoom=True, transition="smoothup",
              fade=0.3, gap=0.15,
              style="힘 있고 또렷한 30대 남성 광고 내레이터. 차분하지만 템포는 조금 빠르게, 핵심 단어에 힘을 주고 문장 끝은 담담하게.",
              music_prompt=("Instrumental only, no vocals, about 60 seconds. Modern upbeat motivational background "
                            "music for a fast-paced product short video: light punchy drums, plucky synth and "
                            "electric guitar, clean and confident, around 112 BPM. Starts with a soft tense 6-second "
                            "intro, then a clear lift, steady energetic groove, ends with a clean resolved hit. "
                            "Unobtrusive under voice-over.")),
}
V = VERSIONS["a"]

CSS = """
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@500;700;900&display=swap');
:root { --o5:#FF6C37; --o6:#F25A22; --o7:#C2410C; --o50:#FFF3EE; --ink:#191F28; }
* { margin:0; padding:0; box-sizing:border-box; }
html, body { width:1080px; height:1920px; overflow:hidden; font-family:'Noto Sans KR', 'Apple SD Gothic Neo', sans-serif; }
body { background:transparent; color:#fff; position:relative; }
em { font-style:normal; color:var(--o5); }
.title { font-size:92px; line-height:1.18; font-weight:900; letter-spacing:-2px; text-shadow:0 4px 30px rgba(0,0,0,.35); }
.sub { margin-top:26px; font-size:38px; line-height:1.45; font-weight:500; color:rgba(255,255,255,.9); }
.sub small { font-size:28px; opacity:.85; }
.shade-top { position:absolute; left:0; right:0; top:0; height:1150px; background:linear-gradient(rgba(10,12,16,.82), rgba(10,12,16,.5) 60%, rgba(10,12,16,0)); }
.shade-bottom { position:absolute; left:0; right:0; bottom:0; height:900px; background:linear-gradient(rgba(10,12,16,0), rgba(10,12,16,.75)); }
.hook { position:absolute; left:84px; right:84px; top:330px; }
.hook .title { font-size:110px; }
.caption { position:absolute; left:84px; right:84px; bottom:300px; }
.caption .title { font-size:96px; }
.top { position:absolute; left:84px; right:84px; top:190px; }
.step { display:inline-block; font-size:34px; font-weight:700; color:#fff; background:var(--o6); padding:10px 24px; border-radius:999px; }
.top .title { margin-top:28px; }
.shot { position:absolute; left:50%; transform:translateX(-50%); border-radius:44px; overflow:hidden; background:#fff;
        box-shadow:0 30px 90px rgba(0,0,0,.45); }
.shot img { display:block; width:100%; height:100%; object-fit:cover; }
.mark { position:absolute; left:0; right:0; bottom:96px; display:flex; justify-content:center; }
.mark img { height:58px; }
body.orange { background:var(--o5); }
body.orange em { color:var(--ink); }
.center { position:absolute; left:84px; right:84px; top:50%; transform:translateY(-50%); }
.center .title { font-size:110px; text-shadow:none; }
.brandmark { height:230px; display:block; margin-bottom:70px; }
.subtitle { position:absolute; left:60px; right:60px; bottom:120px; text-align:center; }
.subtitle span { display:inline; font-size:46px; line-height:1.6; font-weight:700; color:#fff; padding:6px 18px;
                 background:rgba(10,12,16,.72); border-radius:14px; -webkit-box-decoration-break:clone; box-decoration-break:clone; }
.shade-all { position:absolute; inset:0; background:rgba(10,12,16,.55); }
"""


def page(body, cls=""):
    return f'<html><head><meta charset="utf-8"><style>{CSS}</style></head><body class="{cls}">{body}</body></html>'


def scene_html(s):
    a = ASSETS.as_uri()
    kind = s.get("kind", "ui")
    if kind == "outro" and s.get("bg"):
        return page(f'<div class="shade-all"></div><div class="center"><img class="brandmark" src="{a}/ieo_wordmark_white.svg">'
                    f'<div class="title" style="text-shadow:0 4px 30px rgba(0,0,0,.4)">{s["title"]}</div>'
                    f'<div class="sub">{s.get("sub", "")}</div></div>')
    if kind == "outro":
        return page(f'<div class="center"><img class="brandmark" src="{a}/ieo_wordmark_white.svg">'
                    f'<div class="title">{s["title"]}</div><div class="sub">{s.get("sub", "")}</div></div>', "orange")
    if kind == "hook":
        sub = f'<div class="sub">{s["sub"]}</div>' if s.get("sub") else ""
        return page(f'<div class="shade-top"></div><div class="hook"><div class="title">{s["title"]}</div>{sub}</div>')
    if kind == "caption":
        return page(f'<div class="shade-bottom"></div><div class="caption"><div class="title">{s["title"]}</div></div>')
    if kind == "none":
        return page("")
    w, h = s.get("box", (820, 1000))
    bottom = (330 if V["subs"] else 250) + max(0, (1000 - h) // 2)
    return page(f'<div class="shade-top"></div><div class="top"><span class="step">{s["step"]}</span><div class="title">{s["title"]}</div></div>'
                f'<div class="shot" style="width:{w}px; height:{h}px; bottom:{bottom}px">'
                f'<img src="{a}/{s["shot"]}" style="object-position:{s.get("pos", "top")}"></div>'
                + ("" if V["subs"] else f'<div class="mark"><img src="{a}/ieo_wordmark_white.svg"></div>'))


def subtitle_html(text):
    return page(f'<div class="subtitle"><span>{text}</span></div>')


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"실패: {' '.join(cmd[:3])} …\n{r.stderr[-1500:]}")


def duration(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                          "-of", "csv=p=0", str(path)]).decode().strip())


def key():
    for line in ENV.read_text(encoding="utf-8").splitlines():
        if line.startswith("OPENROUTER_API_KEY="):
            return line.split("=", 1)[1].strip().strip('"')
    sys.exit("ieo-server/.env 에 OPENROUTER_API_KEY 가 없어요")


def post(path, body, timeout=300):
    req = urllib.request.Request("https://openrouter.ai/api/v1" + path, method="POST",
                                 headers={"Authorization": "Bearer " + key(), "Content-Type": "application/json"},
                                 data=json.dumps(body, ensure_ascii=False).encode())
    return urllib.request.urlopen(req, timeout=timeout)


def voice_line(text):
    """내레이션 한 줄. 같은 문장·목소리면 저장해 둔 것을 쓴다"""
    folder = OUT / "audio" / "voice"
    folder.mkdir(parents=True, exist_ok=True)
    name = hashlib.sha1(f"{TTS_MODEL}|{TTS_VOICE}|{V['style']}|{text}".encode()).hexdigest()[:16]
    wav = folder / f"{name}.wav"
    if not wav.exists():
        with post("/audio/speech", dict(model=TTS_MODEL, voice=TTS_VOICE, instructions=V["style"], input=text,
                                         response_format="pcm")) as res:
            pcm = res.read()
        raw = folder / f"{name}.pcm"
        raw.write_bytes(pcm)
        # Gemini TTS는 24kHz 16bit 모노 PCM. 앞뒤 무음을 잘라 줄 사이 간격을 우리가 정한다
        run(["ffmpeg", "-y", "-f", "s16le", "-ar", "24000", "-ac", "1", "-i", str(raw), "-af",
             "silenceremove=start_periods=1:start_threshold=-45dB,areverse,"
             "silenceremove=start_periods=1:start_threshold=-45dB,areverse", "-ar", "44100", str(wav)])
        raw.unlink()
    return wav


def music():
    path = OUT / "audio" / V["music"]
    if path.exists():
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    chunks = []
    with post("/chat/completions", dict(model=MUSIC_MODEL, modalities=["text", "audio"], stream=True,
                                         audio={"format": "mp3"},
                                         messages=[{"role": "user", "content": V["music_prompt"]}]), timeout=600) as res:
        for line in res:
            line = line.decode().strip()
            if not line.startswith("data:") or line.endswith("[DONE]"):
                continue
            try:
                delta = json.loads(line[5:])["choices"][0]["delta"]
            except (ValueError, KeyError, IndexError):
                continue
            if (delta.get("audio") or {}).get("data"):
                chunks.append(delta["audio"]["data"])
    if not chunks:
        sys.exit("배경음악을 받지 못했어요")
    path.write_bytes(b"".join(base64.b64decode(c) for c in chunks))
    return path


def render_png(name, html_text, work):
    html = work / f"{name}.html"
    html.write_text(html_text, encoding="utf-8")
    png = OUT / "scenes" / f"{name}.png"
    run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
         "--default-background-color=00000000", f"--window-size={W},{H}", "--virtual-time-budget=4000",
         "--allow-file-access-from-files", f"--screenshot={png}", html.as_uri()])
    return png


def clip(i, s, png, length, sub_png=None):
    """바탕(실사 컷 또는 주황) + 글자·앱 화면. 소리는 실사 컷의 현장 소리만 (내레이션·음악은 마지막에 한 줄로 섞는다)"""
    out = OUT / "clips" / f"{i:02d}.mp4"
    if s.get("bg"):
        src = OUT / "broll" / f"{s['bg']}.mp4"
        if not src.exists():
            sys.exit(f"{src} 가 없어요: python3 design/shorts/broll.py {s['bg']}")
        g = [f"[0:v]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},fps={FPS}[v0]"]
        last = "v0"
        for n, (x, y, w, h) in enumerate(s.get("blur", [])):
            g.append(f"[{last}]split[m{n}][c{n}];[c{n}]crop={w}:{h}:{x}:{y},"
                     f"boxblur=lr=min(w\\,h)/5:lp=4:cr=min(cw\\,ch)/5:cp=4[b{n}];[m{n}][b{n}]overlay={x}:{y}[k{n}]")
            last = f"k{n}"
        if s.get("sharp"):
            # 실사가 주인공: 4초 컷이 모자라면 마지막 화면을 멈춰 잇는다
            g.append(f"[{last}]tpad=stop_mode=clone:stop_duration=20[bg]")
        else:
            # 앱 화면 뒤: 같은 장소 컷을 느리게, 흐리고 어둡게 이어 깐다
            g.append(f"[{last}]setpts=2*PTS,boxblur=lr=24:lp=3:cr=12:cp=3,eq=brightness=-0.12:saturation=0.85,"
                     f"tpad=stop_mode=clone:stop_duration=20[bg]")
        if V["zoom"]:
            # 화면 전체가 천천히 다가간다(조금 크게 키운 뒤 zoompan으로 떨림을 줄인다). 자막은 그 위에 고정
            frames = int(round(length * FPS))
            g.append(f"[bg][1:v]overlay=0:0,scale=1620:2880:flags=lanczos,"
                     f"zoompan=z='1+0.06*on/{frames}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={W}x{H}:fps={FPS}[z]")
            g.append("[z][2:v]overlay=0:0,format=yuv420p[v]" if sub_png else "[z]format=yuv420p[v]")
        else:
            g.append("[bg][1:v]overlay=0:0,format=yuv420p[v]")
        g.append("[0:a]aresample=44100,aformat=channel_layouts=stereo,apad,volume=0.5[a]")
        cmd = ["ffmpeg", "-y", "-stream_loop", "3", "-i", str(src), "-loop", "1", "-i", str(png),
               *(["-loop", "1", "-i", str(sub_png)] if sub_png and V["zoom"] else []),
               "-filter_complex", ";".join(g)]
    else:
        frames = int(round(length * FPS))
        g = [f"[0:v]scale=2160:-1,zoompan=z='1+{0.08 if V['zoom'] else 0.03}*on/{frames}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
             f":d={frames}:s={W}x{H}:fps={FPS},format=yuv420p[v]", "anullsrc=r=44100:cl=stereo[a]"]
        cmd = ["ffmpeg", "-y", "-loop", "1", "-i", str(png), "-filter_complex", ";".join(g)]
    run(cmd + ["-map", "[v]", "-map", "[a]", "-t", f"{length:.3f}", "-r", str(FPS), "-c:v", "libx264",
               "-preset", "medium", "-crf", "18", "-c:a", "aac", "-b:a", "160k", str(out)])
    return out


def main():
    global V
    ver = (sys.argv[1:] or ["a"])[0]
    if ver not in VERSIONS:
        sys.exit(f"모르는 판: {ver} (있는 것: {', '.join(VERSIONS)})")
    V = VERSIONS[ver]
    SCENES, FADE, GAP, sfx = V["scenes"], V["fade"], V["gap"], V["suffix"]
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            sys.exit(f"{tool}이 필요해요: brew install ffmpeg")
    for d in ("scenes", "clips"):  # 판마다 새로 만든다
        shutil.rmtree(OUT / d, ignore_errors=True)
        (OUT / d).mkdir(parents=True)
    work = OUT / ".work"
    work.mkdir(exist_ok=True)

    # 1) 내레이션 줄마다 길이 → 장면 길이. 장면 i는 starts[i]부터 lens[i] 동안 보이고 다음 장면과 FADE만큼 겹친다
    lines, lens = [], []
    for i, s in enumerate(SCENES):
        if s.get("say"):
            wav = voice_line(s["say"])
            lines.append(wav)
            spoken = (LEAD if i == 0 else 0) + duration(wav) + GAP + s.get("tail", 0)
            lens.append(max(spoken, MIN_UI if s.get("shot") else MIN_ANY))
        else:
            lines.append(None)
            lens.append(s["hold"])
    starts = [sum(lens[:i]) for i in range(len(SCENES))]
    total = sum(lens)

    # 2) 장면 영상 (다음 장면과 겹칠 만큼 FADE를 더 길게)
    clips = []
    for i, s in enumerate(SCENES, 1):
        png = render_png(f"{i:02d}", scene_html(s), work)
        # 자막은 앱 화면 장면에만 (실사·끝 장면은 큰 제목이 자막 몫을 한다)
        sub = render_png(f"{i:02d}-sub", subtitle_html(s["say"]), work) if V["subs"] and s.get("shot") else None
        clips.append(clip(i, s, png, lens[i - 1] + (FADE if i < len(SCENES) else 0), sub))

    # 3) 이어 붙이기: 영상은 겹쳐 넘기고, 현장 소리도 겹쳐 넘긴다
    inputs = sum((["-i", str(c)] for c in clips), [])
    vf, af = [], []
    v, a = "[0:v]", "[0:a]"
    for i in range(1, len(clips)):
        vf.append(f"{v}[{i}:v]xfade=transition={V['transition']}:duration={FADE}:offset={starts[i]:.3f}[v{i}]")
        af.append(f"{a}[{i}:a]acrossfade=d={FADE}[a{i}]")
        v, a = f"[v{i}]", f"[a{i}]"
    base = work / "base.mp4"
    run(["ffmpeg", "-y", *inputs, "-filter_complex", ";".join(vf + af), "-map", v, "-map", a,
         "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", str(base)])

    # 4) 내레이션 트랙: 각 줄을 장면 시작 시각에 놓는다
    narr_in, narr_f = [], []
    k = 0
    for i, (wav, st) in enumerate(zip(lines, starts)):
        if wav is None:
            continue
        delay = int((st + (LEAD if i == 0 else 0)) * 1000)
        narr_in += ["-i", str(wav)]
        narr_f.append(f"[{k}:a]aformat=sample_rates=44100:channel_layouts=stereo,adelay={delay}|{delay}[n{k}]")
        k += 1
    narr_f.append("".join(f"[n{j}]" for j in range(k)) + f"amix=inputs={k}:normalize=0:duration=longest,"
                  f"apad,atrim=0:{total:.3f}[narr]")
    narr = work / "narr.wav"
    run(["ffmpeg", "-y", *narr_in, "-filter_complex", ";".join(narr_f), "-map", "[narr]", str(narr)])

    # 5) 섞기: 현장 소리 + 배경음악(말할 때 줄어듦) + 내레이션. 끝 2초는 음악을 줄여 닫는다
    song = music()
    music_f = (f"[1:a]aformat=sample_rates=44100:channel_layouts=stereo,atrim=0:{total:.3f},"
               f"afade=t=in:d=1.5,afade=t=out:st={total - 2:.3f}:d=2")
    mix = (f"[0:a]volume=0.55[amb];{music_f},volume=0.5[mus];"
           f"[2:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=1.6,asplit=2[nar][key];"
           f"[mus][key]sidechaincompress=threshold=0.03:ratio=6:attack=20:release=400[duck];"
           f"[amb][duck][nar]amix=inputs=3:normalize=0:duration=first,alimiter=limit=0.95[out]")
    run(["ffmpeg", "-y", "-i", str(base), "-i", str(song), "-i", str(narr), "-filter_complex", mix,
         "-map", "0:v", "-map", "[out]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
         "-t", f"{total:.3f}", str(OUT / f"ieo-shorts{sfx}.mp4")])
    nonarr = f"[0:a]volume=0.55[amb];{music_f},volume=0.35[mus];[amb][mus]amix=inputs=2:normalize=0:duration=first[out]"
    run(["ffmpeg", "-y", "-i", str(base), "-i", str(song), "-filter_complex", nonarr, "-map", "0:v", "-map", "[out]",
         "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", f"{total:.3f}",
         str(OUT / f"ieo-shorts{sfx}-nonarr.mp4")])

    rows = [f"{i:02d}  {st:5.1f}초부터 {ln:4.1f}초  {s.get('say', '(소리·음악만)')}"
            for i, (s, st, ln) in enumerate(zip(SCENES, starts, lens), 1)]
    (OUT / f"narration{sfx}.txt").write_text("\n".join(rows) + f"\n\n전체 {total:.1f}초\n", encoding="utf-8")
    shutil.rmtree(work)
    (OUT / "ieo-shorts-silent.mp4").unlink(missing_ok=True)
    print(json.dumps({"out": str(OUT / f"ieo-shorts{sfx}.mp4"), "seconds": round(total, 1)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
