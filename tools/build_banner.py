#!/usr/bin/env python3
"""Draw the animated pixel-art banner at the top of the README.

    python3 tools/build_banner.py

writes assets/banner-dark.svg and assets/banner-light.svg: a card battle on a game screen talks
to Claude through the mod's protocol. The game sends its state, Claude answers with commands
(PLAY 1 0, PLAY 2, END), the cards are played, the enemy acts on its intent, a new turn starts,
and every message appears in a log beside the screen. The pixel helpers, palettes, font and the
Claude sprite come from the profile map (Song-Seng-Hun/Song-Seng-Hun tools/build_worldmap.py),
so the banners look alike. 160x90 grid, 4 px per cell, SMIL animation (GitHub plays it in
README images). Standard library only.
"""

import math
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets"

P = 4                 # px per grid cell
W, H = 160, 90        # grid size

# ---------------------------------------------------------------- palettes (from build_worldmap.py)

COMMON = {
    "skin": "#F1C9A5", "hair": "#3B2A22", "glass": "#1A1A2A",
    "coat": "#4A6FA5", "coat_d": "#38598A", "pants": "#2E3A55", "scarf": "#E8C468",
    "orange": "#D97757", "yellow": "#E3B341", "teal": "#4FA3A5",
    "green": "#6DBE73", "blue": "#5E8FD6", "red": "#E5566B",
    "white": "#F7F5EE", "paper": "#FFFDF6", "ink": "#4A5070",
    "flower1": "#E8A0B4", "flower2": "#E8D27A",
}

PALETTES = {
    "dark": {
        **COMMON,
        "K": "#0B0E1C", "frame": "#B89B5E", "frame_d": "#5E4C2C",
        "sky0": "#0C1026", "sky1": "#111735", "sky2": "#171F45", "sky3": "#1D2855", "sky4": "#253366",
        "star": "#F4EBD9", "star_d": "#7F88B5", "cloud": "#2A3566", "cloud_d": "#222C58",
        "mtn_far": "#1A2350", "g0": "#1F3B35", "g1": "#264840", "g2": "#19312C", "tuft": "#2F5A4D",
        "path": "#5C4B3F", "path_e": "#3F332B", "path_h": "#6E5A4B",
        "wall": "#8B8FA8", "wall_m": "#6C7192", "wall_d": "#4C5172",
        "roof": "#7B4A57", "roof_d": "#5A3442", "wood": "#6B4E3A", "wood_d": "#4A3527",
        "metal": "#9AA3B5", "metal_d": "#5F6880", "win": "#262E4D",
        "lit": "#FFD27A", "glow": ("#FFB85C", 0.32), "smoke": ("#9AA3B5", 0.7),
        "text": "#F4EBD9", "sub": "#8E96C2", "shadow": "#0B0E1C",
    },
    "light": {
        **COMMON,
        "K": "#2B2F45", "frame": "#C9A86A", "frame_d": "#8A6E3E",
        "sky0": "#B9D8EA", "sky1": "#C6E0EE", "sky2": "#D2E7F0", "sky3": "#DDEDF1", "sky4": "#E8F2EF",
        "star": "#FFFFFF", "star_d": "#FFFFFF", "cloud": "#FFFFFF", "cloud_d": "#E3ECF2",
        "mtn_far": "#A9C2D4", "g0": "#B4CE98", "g1": "#C1D8A6", "g2": "#A2BE87", "tuft": "#8CB072",
        "path": "#E2D2AE", "path_e": "#C9B48C", "path_h": "#EEE2C6",
        "wall": "#F2EEE6", "wall_m": "#DAD3C6", "wall_d": "#B9B0A1",
        "roof": "#C97E6C", "roof_d": "#A8655A", "wood": "#A47C5B", "wood_d": "#86624A",
        "metal": "#C6CDD6", "metal_d": "#8E97A6", "win": "#7F95AA",
        "lit": "#FFE08A", "glow": ("#FFC94A", 0.38), "smoke": ("#8E97A6", 0.55),
        "text": "#2B2F45", "sub": "#6A7192", "shadow": "#F7F3E8",
    },
}

# sprite characters -> palette keys
CHARS = {
    "K": "K", "h": "hair", "f": "skin", "G": "glass", "c": "coat", "C": "coat_d",
    "n": "pants", "s": "scarf", "o": "orange", "w": "white", "p": "paper", "i": "ink",
    "y": "yellow", "m": "metal", "M": "metal_d", "l": "lit", "r": "red", "g": "green",
    "W": "wood_d", "v": "wood", "R": "roof", "x": "text", "u": "sub",
}

# ---------------------------------------------------------------- canvas (from build_worldmap.py)


class Canvas:
    def __init__(self):
        self.px = {}

    def set(self, x, y, col):
        self.px[(x, y)] = col

    def rect(self, x, y, w, h, col):
        for j in range(y, y + h):
            for i in range(x, x + w):
                self.px[(i, j)] = col

    def box(self, x, y, w, h, fill, edge="K"):
        self.rect(x, y, w, h, edge)
        if w > 2 and h > 2:
            self.rect(x + 1, y + 1, w - 2, h - 2, fill)

    def sprite(self, rows, x, y):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    self.px[(x + i, y + j)] = CHARS[ch]

    def merge(self, other):
        self.px.update(other.px)


def outlined(c, col="K"):
    out = Canvas()
    for (x, y) in c.px:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in c.px:
                out.px[(x + dx, y + dy)] = col
    out.px.update(c.px)
    return out


def rects(cells):
    """Cover a set of cells with few rectangles (row runs merged downwards)."""
    rows = {}
    for x, y in cells:
        rows.setdefault(y, []).append(x)
    out, open_ = [], {}
    for y in sorted(rows):
        xs = sorted(rows[y])
        runs, start, prev = [], xs[0], xs[0]
        for x in xs[1:]:
            if x != prev + 1:
                runs.append((start, prev - start + 1))
                start = x
            prev = x
        runs.append((start, prev - start + 1))
        nxt = {}
        for key in runs:
            if key in open_ and sum(open_[key]) == y:
                y0, h = open_.pop(key)
                nxt[key] = (y0, h + 1)
            else:
                nxt[key] = (y, 1)
        out += [(k[0], y0, k[1], h) for k, (y0, h) in open_.items()]
        open_ = nxt
    out += [(k[0], y0, k[1], h) for k, (y0, h) in open_.items()]
    return out


def svg_paths(c, pal, unit=P, ox=0, oy=0):
    by_col = {}
    for xy, col in c.px.items():
        by_col.setdefault(col, set()).add(xy)
    out = []
    for col in sorted(by_col):
        d = "".join(f"M{x*unit+ox} {y*unit+oy}h{w*unit}v{h*unit}h{-w*unit}z"
                    for x, y, w, h in sorted(rects(by_col[col]), key=lambda r: (r[1], r[0])))
        fill = pal[col]
        if isinstance(fill, tuple):
            out.append(f'<path fill="{fill[0]}" fill-opacity="{fill[1]}" d="{d}"/>')
        else:
            out.append(f'<path fill="{fill}" d="{d}"/>')
    return "".join(out)

# ---------------------------------------------------------------- animation helpers (from build_worldmap.py)

T = 12.0              # loop length in seconds
DT = 1 / 16           # timeline step in seconds
TICKS = [k * DT for k in range(int(T / DT))]
FADE = T - 1.0        # lights go out and everything resets from here


def _kt(t):
    return f"{t / T:.4f}".rstrip("0").rstrip(".")


def _steps(changes):
    ts, vs = [], []
    for t, v in changes:
        if vs and vs[-1] == v:
            continue
        if ts and abs(t - ts[-1]) < 1e-9:
            vs[-1] = v
            continue
        ts.append(t)
        vs.append(v)
    ts[0] = 0.0
    return ";".join(_kt(t) or "0" for t in ts), ";".join(str(v) for v in vs)


def timeline(attr, changes):
    """Discrete animation on the shared T-second loop."""
    k, v = _steps(changes)
    if attr == "translate":
        return (f'<animateTransform attributeName="transform" type="translate" dur="{T}s" '
                f'repeatCount="indefinite" calcMode="discrete" keyTimes="{k}" values="{v}"/>')
    return (f'<animate attributeName="{attr}" dur="{T}s" repeatCount="indefinite" '
            f'calcMode="discrete" keyTimes="{k}" values="{v}"/>')


def loop(attr, dur, values, keytimes=None, begin=0.0, discrete=True, translate=False):
    """Free-running ambient animation with its own period."""
    kt = f' keyTimes="{keytimes}"' if keytimes else ""
    mode = ' calcMode="discrete"' if discrete else ""
    beg = f' begin="{begin:.2f}s"' if begin else ""
    if translate:
        return (f'<animateTransform attributeName="transform" type="translate" dur="{dur}s"'
                f'{beg} repeatCount="indefinite"{mode}{kt} values="{values}"/>')
    return f'<animate attributeName="{attr}" dur="{dur}s"{beg} repeatCount="indefinite"{mode}{kt} values="{values}"/>'


def group(inner, anim="", opacity=None, transform=None):
    attrs = ""
    if opacity is not None:
        attrs += f' opacity="{opacity}"'
    if transform:
        attrs += f' transform="{transform}"'
    return f"<g{attrs}>{inner}{anim}</g>"


def shown(inner, t_on, t_off):
    """Visible from t_on to t_off on the loop."""
    return group(inner, timeline("opacity", [(0, 0), (t_on, 1), (t_off, 0)]), opacity=0)


def sparkle(pal, x, y, t_on):
    small, big = Canvas(), Canvas()
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        small.set(x + dx, y + dy, "lit")
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
        big.set(x + dx, y + dy, "lit" if abs(dx) + abs(dy) < 2 else "white")
    return (group(svg_paths(small, pal), timeline("opacity", [(0, 0), (t_on, 1), (t_on + 0.2, 0),
                                                            (t_on + 0.45, 1), (t_on + 0.65, 0)]), opacity=0)
            + group(svg_paths(big, pal), timeline("opacity", [(0, 0), (t_on + 0.2, 1), (t_on + 0.45, 0)]),
                    opacity=0))

# ---------------------------------------------------------------- font 5x7 (from build_worldmap.py)


FONT_SRC = """
A .###. #...# #...# ##### #...# #...# #...#
B ####. #...# #...# ####. #...# #...# ####.
C .###. #...# #.... #.... #.... #...# .###.
D ####. #...# #...# #...# #...# #...# ####.
E ##### #.... #.... ####. #.... #.... #####
F ##### #.... #.... ####. #.... #.... #....
G .###. #...# #.... #.### #...# #...# .####
H #...# #...# #...# ##### #...# #...# #...#
I ### .#. .#. .#. .#. .#. ###
J ..### ...#. ...#. ...#. ...#. #..#. .##..
K #...# #..#. #.#.. ##... #.#.. #..#. #...#
L #.... #.... #.... #.... #.... #.... #####
M #...# ##.## #.#.# #.#.# #...# #...# #...#
N #...# #...# ##..# #.#.# #..## #...# #...#
O .###. #...# #...# #...# #...# #...# .###.
P ####. #...# #...# ####. #.... #.... #....
Q .###. #...# #...# #...# #.#.# #..#. .##.#
R ####. #...# #...# ####. #.#.. #..#. #...#
S .#### #.... #.... .###. ....# ....# ####.
T ##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..
U #...# #...# #...# #...# #...# #...# .###.
V #...# #...# #...# #...# #...# .#.#. ..#..
W #...# #...# #...# #.#.# #.#.# #.#.# .#.#.
X #...# #...# .#.#. ..#.. .#.#. #...# #...#
Y #...# #...# .#.#. ..#.. ..#.. ..#.. ..#..
Z ##### ....# ...#. ..#.. .#... #.... #####
0 .###. #...# #..## #.#.# ##..# #...# .###.
1 .#. ##. .#. .#. .#. .#. ###
2 .###. #...# ....# ...#. ..#.. .#... #####
3 ####. ....# ....# .###. ....# ....# ####.
4 ...#. ..##. .#.#. #..#. ##### ...#. ...#.
5 ##### #.... ####. ....# ....# #...# .###.
6 .###. #.... #.... ####. #...# #...# .###.
. . . . . . . #
· . . . # . . .
- ... ... ... ### ... ... ...
m ..... ..... ##.#. #.#.# #.#.# #.#.# #.#.#
"""

FONT = {" ": ["..."] * 7}
for line in FONT_SRC.strip().splitlines():
    ch, *rows = line.split()
    FONT[ch] = rows


def text_width(s):
    return sum(len(FONT[ch][0]) + 1 for ch in s) - 1


def draw_text(c, s, x, y, col, shadow="shadow"):
    for pass_col, dy in ((shadow, 1), (col, 0)):
        if pass_col is None:
            continue
        cx = x
        for ch in s:
            for j, row in enumerate(FONT[ch]):
                for i, v in enumerate(row):
                    if v == "#":
                        c.set(cx + i, y + j + dy, pass_col)
            cx += len(FONT[ch][0]) + 1
    return x + text_width(s)

# ---------------------------------------------------------------- sprites (traveler and Claude from build_worldmap.py)


# Claude: body = cols 1-11 x rows 0-7 from the body's top-left, arms beside it, legs on rows 8-9.
CLAUDE_EYES = {
    "happy": [(2, 2), (3, 3), (2, 4), (10, 2), (9, 3), (10, 4)],   # > <
    "dot": [(3, 2), (3, 3), (9, 2), (9, 3)],
    "blink": [(2, 3), (3, 3), (9, 3), (10, 3)],
    "right": [(4, 2), (4, 3), (10, 2), (10, 3)],
    "left": [(2, 2), (2, 3), (8, 2), (8, 3)],
}
_ARM_L, _ARM_R = [(0, 3), (0, 4), (0, 5)], [(12, 3), (12, 4), (12, 5)]
_UP_L, _UP_R = [(0, -2), (0, -1), (0, 0)], [(12, -2), (12, -1), (12, 0)]
CLAUDE_ARMS = {
    "down": _ARM_L + _ARM_R,
    "up": _UP_L + _UP_R,
    "wave1": _UP_L + _ARM_R,
    "wave2": [(0, 1), (-1, 0), (-1, -1)] + _ARM_R,
}
CLAUDE_LEGS = {
    "stand": [(x, y) for x in (2, 4, 8, 10) for y in (8, 9)],
    "stepA": [(2, 8), (2, 9), (8, 8), (8, 9), (4, 8), (10, 8)],
    "stepB": [(4, 8), (4, 9), (10, 8), (10, 9), (2, 8), (8, 8)],
}


def claude_frame(eyes, arms, legs, bob):
    c = Canvas()
    ox, oy = 1, 3 - bob          # two spare rows on top for raised arms
    c.rect(ox + 1, oy, 11, 8, "orange")
    for x, y in CLAUDE_ARMS[arms] + CLAUDE_LEGS[legs]:
        c.set(ox + x, oy + y, "orange")
    for x, y in CLAUDE_EYES[eyes]:
        c.set(ox + x, oy + y, "K")
    return outlined(c)


# ---------------------------------------------------------------- scene

THEME = {
    "dark": {"screen": "#101528", "gw_wall": "#1B2140", "gw_brick": "#232A4E", "gw_floor": "#2A2238",
             "atk": "#C9475C", "skl": "#3E8FC4", "card_e": "#F4EBD9", "gem": "#E3B341", "hp": "#E5566B",
             "hp_bg": "#3B2238", "blob": "#6DBE73", "blob_d": "#3F8A4A", "shield": "#9AA3B5", "out": "#6CC4E8",
             "in": "#E89A6B", "log": "#0B0E1C"},
    "light": {"screen": "#FFFDF6", "gw_wall": "#E3E6EF", "gw_brick": "#D6DAE6", "gw_floor": "#E9DFD0",
              "atk": "#C9475C", "skl": "#3E8FC4", "card_e": "#FFFDF6", "gem": "#D99A20", "hp": "#D9445A",
              "hp_bg": "#E8C9CF", "blob": "#4E9E55", "blob_d": "#2F6E37", "shield": "#8E97A6", "out": "#2F8FC4",
              "in": "#C96A3A", "log": "#F7F3E8"},
}

FLOOR = 81
GAME = (6, 13, 98, 64)                    # the game screen (x, y, w, h)
GW_FLOOR = 55                             # floor line inside the game
LOG = (110, 13, 46, 44)                   # protocol log
HERO = (24, 42)                           # top-left of the hero sprite
ENEMY = (74, 42)                          # top-left of the enemy sprite
CARD_W, CARD_H, CARD_X0, CARD_DX, CARD_Y = 11, 14, 28, 13, 61
CARDS = ["atk", "skl", "atk", "skl", "atk"]
ORB = (15, 66)
CLAUDE_AT = (134, FLOOR - 1)
CABLE_Y = 70

HERO_ROWS = ["..mmmm..", ".mmmmmm.", ".mGGGGm.", ".mmmmmm.", "..cccc.y", ".cccccmy",
             "cccccc.y", ".cccccc.", ".cccccc.", "..n..n..", "..n..n.."]
SHIELD_ROWS = [".sss.", "sssss", "sssss", ".sss.", "..s.."]
SWORD_ROWS = ["....y", "...y.", "y.y..", ".y...", "y.y.."]

# protocol messages: (time sent, direction, text); a message arrives 0.6 s after it is sent
MESSAGES = [(0.4, "out", "STATE"), (1.5, "in", "PLAY 1 0"), (3.2, "out", "STATE"), (4.2, "in", "PLAY 2"),
            (5.8, "out", "STATE"), (6.8, "in", "END"), (9.2, "out", "STATE")]
FLIGHT = 0.6
PLAY1, HIT1 = 2.1, 2.6                    # first card flies to the enemy
PLAY2, BLOCK = 4.8, 5.3                   # second card raises a shield
LUNGE, STRIKE = 7.6, 7.9                  # the enemy attacks
NEW_TURN = 8.6


def snap(x, y):
    return f"{round(x) * P} {round(y) * P}"


def lerp(a, b, u):
    return a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u


def moving(art, path, t_on, t_off):
    pos = [(t, snap(*path(t))) for t in TICKS]
    return group(group(art, timeline("opacity", [(0, 0), (t_on, 1), (t_off, 0)]), opacity=0),
                 timeline("translate", pos))


def rows_canvas(rows, cmap):
    c = Canvas()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                c.set(i, j, cmap[ch])
    return c


def draw_room(c):
    bands = ["sky0", "sky1", "sky2", "sky3", "sky4"]
    for y in range(2, FLOOR):
        k = min(4, (y - 2) * 5 // (FLOOR - 2))
        for x in range(2, W - 2):
            c.set(x, y, bands[k])
    for y in range(FLOOR, H - 2):
        for x in range(2, W - 2):
            c.set(x, y, "wood_d" if (y - FLOOR) % 3 == 0 or (x + 7 * ((y - FLOOR) // 3)) % 23 == 0 else "wood")


def draw_game(c):
    x, y, w, h = GAME
    c.box(x, y, w, h, "gw_wall")
    for j in range(y + 1, GW_FLOOR):                     # brick wall
        for i in range(x + 1, x + w - 1):
            if (j - y) % 5 == 0 or (i + (5 if (j - y) // 5 % 2 else 0)) % 10 == 0:
                c.set(i, j, "gw_brick")
    c.rect(x + 1, GW_FLOOR, w - 2, y + h - 1 - GW_FLOOR, "gw_floor")
    c.box(x + w // 2 - 5, y + h, 11, 2, "metal_d")       # monitor stand
    c.rect(x + w // 2 - 1, y + h + 2, 3, FLOOR - y - h - 2, "metal_d")
    c.rect(x + w // 2 - 8, FLOOR - 1, 17, 1, "metal_d")
    hero = rows_canvas(HERO_ROWS, {"m": "metal", "G": "glass", "c": "coat", "n": "pants", "y": "yellow"})
    c.merge(_shift(outlined(hero), *HERO))
    c.rect(HERO[0] - 1, GW_FLOOR + 1, 11, 2, "hp_bg")       # hp bars
    c.rect(ENEMY[0], GW_FLOOR + 1, 14, 2, "hp_bg")
    x, y, w, h = LOG
    c.box(x, y, w, h, "log")


def _shift(c, dx, dy):
    out = Canvas()
    out.px = {(x + dx, y + dy): col for (x, y), col in c.px.items()}
    return out


def enemy_canvas():
    c = Canvas()
    for j in range(12):
        half = round(7 * math.sqrt(max(0.0, 1 - ((j - 11) / 11.5) ** 2)))
        c.rect(7 - half, j, 2 * half, 1, "blob" if j < 9 else "blob_d")
    for ex in (4, 9):
        c.rect(ex, 5, 2, 2, "K")
    return outlined(c)


def card_canvas(kind):
    c = Canvas()
    c.box(0, 0, CARD_W, CARD_H, "card_e")
    c.rect(1, 1, CARD_W - 2, CARD_H - 2, kind)
    c.rect(2, 4, CARD_W - 4, 5, "card_e")
    art = SWORD_ROWS if kind == "atk" else SHIELD_ROWS
    for j, row in enumerate(art):
        for i, ch in enumerate(row):
            if ch != ".":
                c.set(3 + i, 4 + j, "atk" if kind == "atk" else "skl")
    c.rect(0, 0, 3, 3, "gem")                            # cost gem
    return c


def card_xy(i):
    return CARD_X0 + i * CARD_DX, CARD_Y


def hand_layer(pal):
    out = ""
    for i, kind in enumerate(CARDS):
        x, y = card_xy(i)
        art = svg_paths(card_canvas(kind), pal)
        if i == 0:
            target = (ENEMY[0] + 2, ENEMY[1] - 2)
            played, hit = PLAY1, HIT1
        elif i == 1:
            target = (HERO[0] - 1, HERO[1] - 2)
            played, hit = PLAY2, BLOCK
        else:
            out += group(art, transform=f"translate({x * P} {y * P})")
            continue

        def path(t, x=x, y=y, target=target, played=played, hit=hit):
            if t < played:
                return x, y
            u = min(1.0, (t - played) / (hit - played))
            px, py = lerp((x, y), target, u)
            return px, py - 10 * math.sin(math.pi * u)
        out += moving(art, path, 0, hit)
        out += group(art, timeline("opacity", [(0, 0), (NEW_TURN, 1), (FADE, 0)]), opacity=0,
                     transform=f"translate({x * P} {y * P})")
    return out


def orb_layer(pal):
    x, y = ORB
    base = Canvas()
    for j in range(-5, 6):
        for i in range(-5, 6):
            if i * i + j * j <= 25:
                base.set(x + i, y + j, "orange" if i * i + j * j <= 12 else "K")
    out = svg_paths(base, pal)
    for n, (t_on, t_off) in (("3", (0, PLAY1)), ("2", (PLAY1, PLAY2)), ("1", (PLAY2, NEW_TURN)),
                             ("3 ", (NEW_TURN, FADE + 0.01))):
        lab = Canvas()
        draw_text(lab, n.strip(), x * 2 - 2, y * 2 - 3, "white", shadow=None)
        if t_on == 0:
            out += group(svg_paths(lab, pal, unit=2),
                         timeline("opacity", [(0, 1), (t_off, 0), (FADE, 1)]))
        else:
            out += shown(svg_paths(lab, pal, unit=2), t_on, min(t_off, FADE))
    return out


def battle_layer(pal):
    out = ""
    enemy = svg_paths(enemy_canvas(), pal)
    lunge = [(0, snap(*ENEMY)), (LUNGE, snap(ENEMY[0] - 8, ENEMY[1])), (STRIKE + 0.15, snap(*ENEMY))]
    out += group(enemy, timeline("translate", lunge))
    intent = Canvas()                                    # intent: an attack for 12
    sword = rows_canvas(SWORD_ROWS, {"y": "hp"})
    intent.merge(_shift(outlined(sword), ENEMY[0] + 2, ENEMY[1] - 9))
    lab = Canvas()
    draw_text(lab, "12", (ENEMY[0] + 9) * 2, (ENEMY[1] - 9) * 2, "text", shadow=None)
    art = svg_paths(intent, pal) + svg_paths(lab, pal, unit=2)
    out += group(art, timeline("opacity", [(0, 1), (LUNGE, 0), (NEW_TURN, 1)]))
    for who, x0, width, drop_at, keep in ((ENEMY, ENEMY[0], 14, HIT1, 10), (HERO, HERO[0] - 1, 11, STRIKE, 9)):
        full, part = Canvas(), Canvas()
        full.rect(x0, GW_FLOOR + 1, width, 2, "hp")
        part.rect(x0, GW_FLOOR + 1, keep, 2, "hp")
        out += group(svg_paths(full, pal), timeline("opacity", [(0, 1), (drop_at, 0), (FADE, 1)]))
        out += shown(svg_paths(part, pal), drop_at, FADE)
        out += sparkle(pal, x0 + width // 2, who[1] + 4, drop_at)
    dmg = Canvas()
    draw_text(dmg, "6", (ENEMY[0] + 12) * 2, 0, "hp", shadow=None)
    out += moving(svg_paths(dmg, pal, unit=2), lambda t: (0, ENEMY[1] - 2 - 3 * min(1.0, max(0.0, t - HIT1))),
                  HIT1, HIT1 + 0.7)
    shield = Canvas()
    shield.merge(_shift(outlined(rows_canvas(SHIELD_ROWS, {"s": "shield"})), HERO[0] - 7, HERO[1] + 3))
    blk = Canvas()
    draw_text(blk, "5", (HERO[0] - 6) * 2, (HERO[1] + 10) * 2, "text", shadow=None)
    out += shown(svg_paths(shield, pal) + svg_paths(blk, pal, unit=2), BLOCK, STRIKE)
    return out


def log_layer(pal):
    """Each message travels along the cable, then its line appears in the log."""
    out = ""
    a, b = (GAME[0] + GAME[2], CABLE_Y), (CLAUDE_AT[0] - 9, CABLE_Y)
    cable = Canvas()
    cable.rect(a[0], CABLE_Y + 1, b[0] - a[0], 1, "metal_d")
    out += svg_paths(cable, pal)
    for n, (t, way, text) in enumerate(MESSAGES):
        src, dst = (a, b) if way == "out" else (b, a)
        pkt = Canvas()
        pkt.rect(0, 0, 4, 3, way)
        pkt.set(1, 1, "K"), pkt.set(2, 1, "K")

        def path(tt, t=t, src=src, dst=dst):
            u = min(1.0, max(0.0, (tt - t) / FLIGHT))
            return lerp((src[0], src[1] - 2), (dst[0] - 4, dst[1] - 2), u)
        out += moving(svg_paths(outlined(pkt), pal), path, t, t + FLIGHT)
        line = Canvas()
        lx, ly = LOG[0] + 3, LOG[1] + 3 + n * 6
        line.rect(lx, ly + 1, 1, 2, way)
        draw_text(line, text, (lx + 3) * 2, ly * 2, way, shadow=None)
        appear = t + FLIGHT if way == "out" else t
        out += shown(svg_paths(_halfcell(line, lx, ly), pal, unit=2), appear, FADE)
    return out


def _halfcell(c, lx, ly):
    """The log mixes grid cells (the marker) and half cells (the text); express all in half cells."""
    out = Canvas()
    for (x, y), col in c.px.items():
        if x == lx and ly <= y <= ly + 3:
            for dx in (0, 1):
                for dy in (0, 1):
                    out.set(2 * x + dx, 2 * y + dy, col)
        else:
            out.set(x, y, col)
    return out


def claude_pose(t):
    for t0, way, _ in MESSAGES:
        if way == "in" and t0 - 0.3 <= t < t0 + 0.2:
            return ("happy", "wave1", 0)                 # sends a command
    if HIT1 <= t < HIT1 + 0.6 or 9.8 <= t < FADE:
        return ("happy", "up", 0)
    for t0, way, _ in MESSAGES:
        if way == "out" and t0 + FLIGHT <= t < t0 + FLIGHT + 0.3:
            return ("dot", "down", 1)                    # reads the state
    blink = any(a <= t < a + 0.125 for a in (0.15, 5.2, FADE + 0.4))
    return ("blink" if blink else "left", "down", 0)


def claude_layer(pal):
    seq = [(t, claude_pose(t)) for t in TICKS]
    frames = {pose: claude_frame(pose[0], pose[1], "stand", pose[2]) for pose in dict.fromkeys(p for _, p in seq)}
    ox, oy = (CLAUDE_AT[0] - 7) * P, (CLAUDE_AT[1] - 13) * P
    return "".join(group(svg_paths(fc, pal, ox=ox, oy=oy),
                         timeline("opacity", [(t, 1 if p == pose else 0) for t, p in seq]),
                         opacity=1 if pose == seq[0][1] else 0)
                   for pose, fc in frames.items())


def frame_border():
    f = Canvas()
    f.rect(0, 0, W, H, "frame_d")
    f.rect(1, 1, W - 2, H - 2, "frame")
    for y in range(2, H - 2):
        for x in range(2, W - 2):
            f.px.pop((x, y), None)
    for x, y in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        f.px.pop((x, y), None)
    for x, y in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        f.set(x, y, "frame_d")
    return f


def build(theme):
    pal = {**PALETTES[theme], **THEME[theme]}
    bg = Canvas()
    draw_room(bg)
    draw_game(bg)
    title = Canvas()                                     # unit 3 px
    draw_text(title, "COMMUNICATIONMOD", W * P // 6 - text_width("COMMUNICATIONMOD") // 2, 4, "text")
    desc = ("Pixel-art banner: a card battle on a game screen talks to Claude over a cable. The game sends "
            "STATE, Claude answers PLAY 1 0 and an attack card flies at the enemy for 6 damage; after the next "
            "STATE Claude answers PLAY 2 and a skill card gives the hero a shield of 5; then Claude sends END, the "
            "enemy carries out its intent and lunges, a new turn refills the hand and energy, and the game sends "
            "STATE again. Each message appears in a log beside the screen.")
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W * P} {H * P}" width="{W * P}" '
        f'height="{H * P}" shape-rendering="crispEdges" role="img" aria-labelledby="t d">'
        f'<title id="t">CommunicationMod</title><desc id="d">{desc}</desc>'
        + svg_paths(bg, pal) + battle_layer(pal) + orb_layer(pal) + hand_layer(pal) + log_layer(pal)
        + claude_layer(pal) + svg_paths(title, pal, unit=3) + svg_paths(frame_border(), pal)
        + "</svg>\n"
    )


def main():
    OUT.mkdir(exist_ok=True)
    for theme in PALETTES:
        path = OUT / f"banner-{theme}.svg"
        path.write_text(build(theme), encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
