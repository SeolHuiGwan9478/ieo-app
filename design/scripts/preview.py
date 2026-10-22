import sys, re
L, out = sys.argv[1], sys.argv[2]
def inner(p, x, y, w, h):
    s = open(f"{L}/{p}").read()
    vb = re.search(r'viewBox="([^"]+)"', s).group(1)
    return f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="{vb}">{s[s.index(">") + 1:s.rindex("</svg>")]}</svg>'
rows = []
# 흰 바탕에는 주황 로고, 주황·어두운 바탕에는 흰 로고
for i, (bg, icon, c) in enumerate([("#FFFFFF", "orange", "orange"), ("#FF6C37", "white", "white"), ("#191F28", "orange", "white")]):
    y = i * 160
    rows += [f'<rect x="0" y="{y}" width="520" height="160" fill="{bg}"/>',
             inner(f"icon/ieo_icon_{icon}.svg", 20, y + 30, 100, 100),
             inner(f"icon/ieo_symbol_{c}.svg", 140, y + 65, 110, 30),
             inner(f"wordmark/ieo_wordmark_{c}.svg", 270, y + 30, 230, 100)]
open(out, "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 480">{"".join(rows)}</svg>')
