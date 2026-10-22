import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

font_path, out = sys.argv[1], sys.argv[2]
BLACK, WHITE = "#000000", "#FFFFFF"
TEXT = "ieo."
WEIGHT = 800
TRACKING = -0.03            # 자간 (em 단위, 음수면 좁아짐)

# ---- 텍스트 로고: Outfit 글자 윤곽을 path로 변환 ----
font = instantiateVariableFont(TTFont(font_path), {"wght": WEIGHT})
gs, cmap = font.getGlyphSet(), font.getBestCmap()
upm = font["head"].unitsPerEm
s = 100.0 / upm
xh = font["OS/2"].sxHeight * s
PAD = 8

glyphs = [gs[cmap[ord(c)]] for c in TEXT]
tops, bots = [], []
for g in glyphs:
    bp = BoundsPen(gs); g.draw(bp)
    tops.append(bp.bounds[3]); bots.append(bp.bounds[1])
BASE = PAD + max(tops) * s

def lsb(g):
    bp = BoundsPen(gs); g.draw(bp); return bp.bounds[0]

x = PAD - lsb(glyphs[0]) * s
paths = []
for i, g in enumerate(glyphs):
    pen = SVGPathPen(gs)
    g.draw(TransformPen(pen, (s, 0, 0, -s, x, BASE)))
    paths.append(f'<path d="{pen.getCommands()}"/>')
    x += g.width * s + (TRACKING * 100 if i < len(glyphs) - 1 else 0)
last = glyphs[-1]
bp = BoundsPen(gs); last.draw(bp)
right = x - last.width * s + bp.bounds[2] * s
WM_W, WM_H = right + PAD, BASE - min(bots) * s + PAD
WORD = "".join(paths)

# ---- 아이콘: 누운 i와 작아지는 점 ----
GLYPH = '<rect x="18" y="51" width="42" height="18" rx="9"/><circle cx="76" cy="60" r="9"/><circle cx="98" cy="60" r="6"/>'
def icon(bg, fg):
    return f'<rect width="120" height="120" rx="26" fill="{bg}"/><g fill="{fg}">{GLYPH}</g>'

def write(path, w, h, body, vb_x=0, vb_y=0):
    open(f"{out}/{path}", "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb_x} {vb_y} {w:.0f} {h:.0f}">{body}</svg>\n')

import os
for d in ("icon", "wordmark"):
    os.makedirs(f"{out}/{d}", exist_ok=True)

# 파일 이름의 색 = 로고 색. black은 밝은 배경용, white는 어두운 배경용
write("icon/ieo_icon_black.svg", 120, 120, icon(BLACK, WHITE))
write("icon/ieo_icon_white.svg", 120, 120, icon(WHITE, BLACK))
write("icon/ieo_symbol_black.svg", 94, 26, f'<g fill="{BLACK}">{GLYPH}</g>', 14, 47)
write("icon/ieo_symbol_white.svg", 94, 26, f'<g fill="{WHITE}">{GLYPH}</g>', 14, 47)
write("wordmark/ieo_wordmark_black.svg", WM_W, WM_H, f'<g fill="{BLACK}">{WORD}</g>')
write("wordmark/ieo_wordmark_white.svg", WM_W, WM_H, f'<g fill="{WHITE}">{WORD}</g>')

print("wordmark", round(WM_W), round(WM_H))
