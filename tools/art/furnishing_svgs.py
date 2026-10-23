"""The furnishings yards sell for the crew's downtime (assets/data/furnishings.toml): couches, game
tables, an arcade cabinet... into assets/art/decor. Each canvas is the piece's footprint at 64 units a
tile, its back toward the top of the canvas and its front toward the bottom. generate_svgs.py runs it
after the rest of the decor; run it alone to redraw just these:

    python tools/art/furnishing_svgs.py
"""
import math
import os
import sys

O = '#10141a'


def furnishings(out):
    """Writes every furnishing's SVG into the folder out (assets/art/decor)."""
    def svg(name, w, h, title, base, emissive=None, meta=None):
        s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
             f'  <title>{name}: {title}</title>', '  <g id="base">']
        s += ['    ' + l for l in base]
        s.append('  </g>')
        if emissive:
            s.append('  <g id="emissive">')
            s += ['    ' + l for l in emissive]
            s.append('  </g>')
        if meta is None:
            meta = [f'<rect id="collider" x="1" y="1" width="{w-2}" height="{h-2}" fill="none" stroke="none"/>']
        s.append('  <g id="meta">')
        s += ['    ' + l for l in meta]
        s.append('  </g>')
        s.append('</svg>')
        with open(os.path.join(out, name + '.svg'), 'w') as f:
            f.write('\n'.join(s) + '\n')

    # Couch: 1.5 x 0.75 tiles, three cushions, back along the top
    fab, fabL, fabD = '#5b4a7a', '#7a68a0', '#3f3256'
    svg('couch', 96, 48, 'three-seat couch, back toward +y', [
        f'<rect x="2" y="2" width="92" height="44" rx="6" fill="{fabD}" stroke="{O}" stroke-width="2"/>',
        f'<rect x="4" y="4" width="88" height="12" rx="4" fill="{fab}" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="4" y="14" width="10" height="30" rx="4" fill="{fab}" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="82" y="14" width="10" height="30" rx="4" fill="{fab}" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="15" y="17" width="21.5" height="26" rx="3" fill="{fabL}" stroke="{O}" stroke-width="1"/>',
        f'<rect x="37.5" y="17" width="21" height="26" rx="3" fill="{fabL}" stroke="{O}" stroke-width="1"/>',
        f'<rect x="59.5" y="17" width="21.5" height="26" rx="3" fill="{fabL}" stroke="{O}" stroke-width="1"/>',
        f'<path d="M6,6.5 L90,6.5" stroke="#9a88c0" stroke-width="1" fill="none" stroke-linecap="round"/>',
    ])

    # Armchair: 0.75 x 0.75
    fab, fabL, fabD = '#7a4a3a', '#a06a55', '#56332a'
    svg('armchair', 48, 48, 'armchair, back toward +y', [
        f'<rect x="2" y="2" width="44" height="44" rx="6" fill="{fabD}" stroke="{O}" stroke-width="2"/>',
        f'<rect x="4" y="4" width="40" height="12" rx="4" fill="{fab}" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="4" y="14" width="9" height="30" rx="4" fill="{fab}" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="35" y="14" width="9" height="30" rx="4" fill="{fab}" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="14" y="17" width="20" height="26" rx="3" fill="{fabL}" stroke="{O}" stroke-width="1"/>',
        f'<path d="M6,6.5 L42,6.5" stroke="#c08a72" stroke-width="1" fill="none" stroke-linecap="round"/>',
    ])

    # Beanbag: round, 0.75
    svg('beanbag', 48, 48, 'beanbag, slumped toward +y', [
        f'<path d="M24,3 C38,3 45,12 45,25 C45,38 36,45 24,45 C12,45 3,38 3,25 C3,12 10,3 24,3 Z" fill="#c27a2c" stroke="{O}" stroke-width="2"/>',
        f'<ellipse cx="24" cy="29" rx="13" ry="10" fill="#9a5e1e" stroke="{O}" stroke-width="1"/>',
        '<path d="M11,13 Q24,5 37,13" stroke="#e4a052" stroke-width="1.5" fill="none" stroke-linecap="round"/>',
    ], meta=['<circle id="collider" cx="24" cy="24" r="21" fill="none" stroke="none"/>'])

    # Coffee table: 0.75 x 0.5
    svg('coffee_table', 48, 32, 'low coffee table', [
        f'<rect x="2" y="2" width="44" height="28" rx="3" fill="#6b5a48" stroke="{O}" stroke-width="2"/>',
        '<path d="M3.5,28.5 L3.5,3.5 L44.5,3.5" fill="none" stroke="#8a755e" stroke-width="1" stroke-linejoin="round"/>',
        f'<rect x="8" y="9" width="10" height="7" rx="1" fill="#c9c2b0" stroke="{O}" stroke-width="0.8"/>',
        f'<circle cx="34" cy="20" r="4" fill="#d9d4c8" stroke="{O}" stroke-width="0.8"/>',
        '<circle cx="34" cy="20" r="2.2" fill="#5a3a24"/>',
    ])

    # Card table: 1 x 1, green baize, cards dealt to four
    cards = []
    for (x, y, r) in [(32, 14, 0), (32, 50, 0), (14, 32, 90), (50, 32, 90)]:
        cards.append(f'<rect x="{x-4}" y="{y-3}" width="8" height="6" rx="1" fill="#f2efe6" stroke="{O}" stroke-width="0.6" transform="rotate({r} {x} {y})"/>')
    svg('card_table', 64, 64, 'round card table, cards dealt to four', [
        f'<circle cx="32" cy="32" r="29" fill="#5a4030" stroke="{O}" stroke-width="2"/>',
        f'<circle cx="32" cy="32" r="24" fill="#2f6a44" stroke="{O}" stroke-width="1"/>',
        '<circle cx="32" cy="32" r="22" fill="none" stroke="#3f8456" stroke-width="1"/>',
        *cards,
        f'<rect x="27" y="28" width="10" height="8" rx="1" fill="#b8323a" stroke="{O}" stroke-width="0.8"/>',
        '<circle cx="22" cy="40" r="2" fill="#e8c040" stroke="#10141a" stroke-width="0.5"/>',
        '<circle cx="43" cy="24" r="2" fill="#4a7ad8" stroke="#10141a" stroke-width="0.5"/>',
    ], meta=['<circle id="collider" cx="32" cy="32" r="29" fill="none" stroke="none"/>'])

    # Chess table: 0.75 x 0.75, board with pieces
    sq = []
    for i in range(8):
        for j in range(8):
            if (i + j) % 2 == 0:
                sq.append(f'<rect x="{10 + i*3.5}" y="{10 + j*3.5}" width="3.5" height="3.5" fill="#d9cfb4"/>')
    pcs = []
    for i in range(8):
        pcs.append(f'<circle cx="{11.75 + i*3.5}" cy="13.5" r="1.2" fill="#1c1c1c"/>')
        pcs.append(f'<circle cx="{11.75 + i*3.5}" cy="34.5" r="1.2" fill="#f4f0e4" stroke="#10141a" stroke-width="0.3"/>')
    svg('chess_table', 48, 48, 'chess table, a game under way', [
        f'<rect x="2" y="2" width="44" height="44" rx="4" fill="#4a3424" stroke="{O}" stroke-width="2"/>',
        f'<rect x="10" y="10" width="28" height="28" fill="#5c4632" stroke="{O}" stroke-width="1"/>',
        *sq, *pcs,
    ])

    # Pool table: 2 x 1
    balls = [('#e8c040', 84, 32), ('#2a5ad0', 89, 29), ('#c83030', 89, 35), ('#6a2a9a', 94, 26), ('#e07020', 94, 32),
             ('#1c1c1c', 94, 38), ('#2a8a3a', 99, 29), ('#8a2a2a', 99, 35)]
    svg('pool_table', 128, 64, 'pool table, racked', [
        f'<rect x="2" y="2" width="124" height="60" rx="6" fill="#5a3a22" stroke="{O}" stroke-width="2"/>',
        f'<rect x="10" y="10" width="108" height="44" rx="2" fill="#1f6a4a" stroke="{O}" stroke-width="1.2"/>',
        *[f'<circle cx="{x}" cy="{y}" r="3.5" fill="#0c0f12"/>' for (x, y) in [(10, 10), (64, 9), (118, 10), (10, 54), (64, 55), (118, 54)]],
        '<path d="M36,12 L36,52" stroke="#2c805c" stroke-width="1" stroke-dasharray="2 2"/>',
        *[f'<circle cx="{x}" cy="{y}" r="2.6" fill="{c}" stroke="{O}" stroke-width="0.6"/>' for (c, x, y) in balls],
        f'<circle cx="30" cy="32" r="2.6" fill="#f4f0e4" stroke="{O}" stroke-width="0.6"/>',
        '<path d="M4,60 L22,34" stroke="#c8a070" stroke-width="2" stroke-linecap="round"/>',
    ])

    # Arcade cabinet: 0.75 x 0.5, screen and controls toward the front (-y, SVG bottom)
    svg('arcade_cabinet', 48, 32, 'arcade cabinet, controls toward -y', [
        f'<rect x="2" y="2" width="44" height="28" rx="3" fill="#2a2440" stroke="{O}" stroke-width="2"/>',
        f'<rect x="6" y="4" width="36" height="14" rx="1.5" fill="#0c1a26" stroke="{O}" stroke-width="1"/>',
        f'<rect x="4" y="20" width="40" height="8" rx="1.5" fill="#3a3456" stroke="{O}" stroke-width="1"/>',
        f'<circle cx="12" cy="24" r="2.2" fill="#1b2028" stroke="{O}" stroke-width="0.6"/>',
        '<path d="M3,2.5 L45,2.5" stroke="#d04ab0" stroke-width="1.2"/>',
    ], emissive=[
        '<g data-light="point" data-reach="2" data-intensity="0.35">',
        '<rect x="7" y="5" width="34" height="12" rx="1" fill="#2ad0e0"/>',
        '<rect x="11" y="8" width="6" height="4" fill="#f2e040"/>',
        '<rect x="26" y="7" width="10" height="2" fill="#f04a8a"/>',
        '</g>',
        '<g data-light="glow">',
        '<circle cx="24" cy="24" r="1.8" fill="#f04a4a"/>',
        '<circle cx="30" cy="24" r="1.8" fill="#4af06a"/>',
        '<circle cx="36" cy="24" r="1.8" fill="#4a8af0"/>',
        '</g>',
    ])

    # Media screen on a stand: 1.5 x 0.25, facing -y
    svg('media_screen', 96, 16, 'big media screen on a stand, facing -y', [
        f'<rect x="2" y="2" width="92" height="12" rx="2" fill="#20252e" stroke="{O}" stroke-width="2"/>',
        f'<rect x="5" y="8" width="86" height="4" fill="#0c1a26" stroke="{O}" stroke-width="0.8"/>',
        f'<rect x="40" y="3" width="16" height="4" fill="#373f4b" stroke="{O}" stroke-width="0.8"/>',
    ], emissive=[
        '<g data-light="cone" data-reach="3" data-intensity="0.4" data-direction="-90" data-cone="70">',
        '<rect x="6" y="9" width="84" height="2" fill="#7ac8ff"/>',
        '<path d="M5,12 L91,12 L96,16 L0,16 Z" fill="#7ac8ff" opacity="0.25"/>',
        '</g>',
    ])

    # Jukebox: 0.75 x 0.5, arched top toward +y
    svg('jukebox', 48, 32, 'jukebox, front toward -y', [
        f'<path d="M3,30 L3,14 Q3,2 24,2 Q45,2 45,14 L45,30 Z" fill="#7a2a2a" stroke="{O}" stroke-width="2"/>',
        f'<path d="M8,28 L8,15 Q8,7 24,7 Q40,7 40,15 L40,28 Z" fill="#3a1414" stroke="{O}" stroke-width="1"/>',
        f'<rect x="12" y="20" width="24" height="6" rx="1" fill="#c8b070" stroke="{O}" stroke-width="0.8"/>',
    ], emissive=[
        '<g data-light="point" data-reach="2" data-intensity="0.3">',
        '<path d="M9,15 Q9,8 24,8 Q39,8 39,15" fill="none" stroke="#f0a040" stroke-width="2"/>',
        '<path d="M12,16 Q12,11 24,11 Q36,11 36,16" fill="none" stroke="#f04a8a" stroke-width="1.5"/>',
        '<path d="M15,17 Q15,14 24,14 Q33,14 33,17" fill="none" stroke="#4ad0f0" stroke-width="1.5"/>',
        '</g>',
    ])

    # Keyboard (synth on a stand): 1 x 0.5
    keys = [f'<rect x="{6 + i*4.4}" y="15" width="4" height="12" fill="#f4f0e4" stroke="{O}" stroke-width="0.4"/>' for i in range(12)]
    blk = [f'<rect x="{9 + i*4.4}" y="15" width="2.6" height="7" fill="#10141a"/>' for i in range(11) if i % 7 not in (2, 6)]
    svg('keyboard', 64, 32, 'keyboard on its stand, keys toward -y', [
        f'<rect x="2" y="4" width="60" height="25" rx="3" fill="#2a2e36" stroke="{O}" stroke-width="2"/>',
        f'<rect x="6" y="7" width="20" height="5" rx="1" fill="#0c1a26" stroke="{O}" stroke-width="0.6"/>',
        *[f'<circle cx="{32 + i*6}" cy="9.5" r="1.8" fill="#5b6675" stroke="{O}" stroke-width="0.5"/>' for i in range(5)],
        *keys, *blk,
    ], emissive=['<g data-light="glow"><rect x="7" y="8" width="18" height="3" fill="#6af0a0"/></g>'])

    # Bookshelf: 1 x 0.375, books seen from above, spines toward -y
    colors = ['#a03a3a', '#3a6aa0', '#c8a040', '#4a8a4a', '#7a4a9a', '#d07030', '#3a8a8a', '#9a9a9a', '#b04a6a', '#5a7a3a']
    books, x = [], 5
    for i, c in enumerate(colors * 2):
        w = 2.6 + (i * 7 % 3) * 0.6
        if x + w > 59: break
        books.append(f'<rect x="{x:.1f}" y="{6 + (i*5 % 3)}" width="{w:.1f}" height="{14 - (i*5 % 3)}" fill="{c}" stroke="{O}" stroke-width="0.4"/>')
        x += w + 0.3
    svg('bookshelf', 64, 24, 'bookshelf, spines toward -y', [
        f'<rect x="2" y="2" width="60" height="20" rx="2" fill="#5a4030" stroke="{O}" stroke-width="2"/>',
        f'<rect x="4" y="4" width="56" height="16" fill="#3a2a20" stroke="{O}" stroke-width="0.8"/>',
        *books,
    ])

    # Exercise bike: 0.5 x 1, rider faces -y
    svg('exercise_bike', 32, 64, 'exercise bike, rider faces -y', [
        f'<rect x="8" y="4" width="16" height="56" rx="4" fill="#373f4b" stroke="{O}" stroke-width="2"/>',
        f'<ellipse cx="16" cy="16" rx="7" ry="9" fill="#1e2227" stroke="{O}" stroke-width="1.2"/>',
        f'<circle cx="16" cy="36" r="8" fill="#5b6675" stroke="{O}" stroke-width="1.5"/>',
        '<path d="M6,36 L26,36" stroke="#10141a" stroke-width="2.5" stroke-linecap="round"/>',
        f'<rect x="4" y="50" width="24" height="5" rx="2.5" fill="#7d8898" stroke="{O}" stroke-width="1.2"/>',
        f'<rect x="12" y="54" width="8" height="5" rx="1" fill="#0c1a26" stroke="{O}" stroke-width="0.6"/>',
    ], emissive=['<g data-light="glow"><rect x="13" y="55" width="6" height="3" fill="#6af0a0"/></g>'])

    # Weight bench: 0.5 x 1.25, barbell across the rack at the back
    svg('weight_bench', 32, 80, 'weight bench, barbell racked at the +y end', [
        f'<rect x="9" y="14" width="14" height="62" rx="4" fill="#2b2f36" stroke="{O}" stroke-width="2"/>',
        f'<rect x="11" y="16" width="10" height="30" rx="3" fill="#a03a3a" stroke="{O}" stroke-width="1"/>',
        f'<rect x="11" y="48" width="10" height="26" rx="3" fill="#a03a3a" stroke="{O}" stroke-width="1"/>',
        f'<rect x="3" y="8" width="3" height="10" fill="#5b6675" stroke="{O}" stroke-width="0.8"/>',
        f'<rect x="26" y="8" width="3" height="10" fill="#5b6675" stroke="{O}" stroke-width="0.8"/>',
        '<path d="M1,10 L31,10" stroke="#7d8898" stroke-width="2" stroke-linecap="round"/>',
        f'<rect x="0.5" y="4" width="4" height="12" rx="1" fill="#1e2227" stroke="{O}" stroke-width="0.8"/>',
        f'<rect x="27.5" y="4" width="4" height="12" rx="1" fill="#1e2227" stroke="{O}" stroke-width="0.8"/>',
    ])

    # Dartboard on a wall: 0.5 x 0.125, seen edge-on from above
    svg('dartboard', 32, 16, 'dartboard hung on a wall at the top, facing -y', [
        f'<rect x="2" y="1" width="28" height="4" fill="#3a2a20" stroke="{O}" stroke-width="1"/>',
        f'<rect x="4" y="5" width="24" height="5" rx="1" fill="#1c1c1c" stroke="{O}" stroke-width="1"/>',
        '<rect x="6" y="6" width="5" height="3" fill="#c83030"/>',
        '<rect x="13" y="6" width="6" height="3" fill="#2a8a3a"/>',
        '<rect x="21" y="6" width="5" height="3" fill="#c83030"/>',
        '<path d="M11,10 L10,14 M16,10 L16,15 M22,10 L23,14" stroke="#c8a070" stroke-width="1" stroke-linecap="round"/>',
    ])

    # Aquarium: 1 x 0.5
    svg('aquarium', 64, 32, 'fish tank on a cabinet', [
        f'<rect x="2" y="2" width="60" height="28" rx="2" fill="#2f3641" stroke="{O}" stroke-width="2"/>',
        f'<rect x="5" y="5" width="54" height="22" fill="#14405a" stroke="{O}" stroke-width="1"/>',
        '<path d="M6,24 Q14,20 20,24 T34,23 T50,24 T58,22 L58,26 L6,26 Z" fill="#c8b080"/>',
        '<path d="M14,24 Q12,16 15,9 M16,24 Q18,17 16,11 M46,24 Q44,15 48,10" stroke="#3ab060" stroke-width="1.5" fill="none" stroke-linecap="round"/>',
    ], emissive=[
        '<g data-light="point" data-reach="2" data-intensity="0.3">',
        '<rect x="6" y="6" width="52" height="3" fill="#6ad0f0" opacity="0.7"/>',
        '<path d="M26,14 l5,-2 l0,4 Z M30,14 a2.5,1.6 0 1 0 0.01,0" fill="#f0a040"/>',
        '<path d="M40,19 l-4,-1.6 l0,3.2 Z M37,19 a2,1.3 0 1 1 -0.01,0" fill="#f04a8a"/>',
        '</g>',
    ])

    # Planter: 0.5 x 0.5, a leafy plant in a square pot
    leaves = []
    for k in range(7):
        a = k * 2 * math.pi / 7 + 0.3
        x2, y2 = 16 + 12 * math.cos(a), 16 + 12 * math.sin(a)
        cx, cy = 16 + 7 * math.cos(a + 0.5), 16 + 7 * math.sin(a + 0.5)
        leaves.append(f'<path d="M16,16 Q{cx:.1f},{cy:.1f} {x2:.1f},{y2:.1f}" fill="none" stroke="#4ab45a" stroke-width="3" stroke-linecap="round"/>')
    svg('planter', 32, 32, 'leafy plant in a square planter', [
        f'<rect x="3" y="3" width="26" height="26" rx="3" fill="#5a4a3a" stroke="{O}" stroke-width="2"/>',
        '<rect x="6" y="6" width="20" height="20" rx="2" fill="#3a2e22"/>',
        *leaves,
        '<circle cx="16" cy="16" r="2.5" fill="#8fe39a"/>',
    ])

    # Rug: 1.5 x 1, flat
    svg('rug', 96, 64, 'patterned rug', [
        f'<rect x="2" y="2" width="92" height="60" rx="2" fill="#8a3a2a" stroke="{O}" stroke-width="1.5"/>',
        '<rect x="7" y="7" width="82" height="50" fill="none" stroke="#d8a050" stroke-width="2"/>',
        '<rect x="12" y="12" width="72" height="40" fill="#6a2a20"/>',
        '<path d="M48,16 L76,32 L48,48 L20,32 Z" fill="#c87a3a" stroke="#d8a050" stroke-width="1.5"/>',
        '<path d="M48,24 L62,32 L48,40 L34,32 Z" fill="#2a4a6a"/>',
        *[f'<path d="M{x},2 L{x},0 M{x},62 L{x},64" stroke="#d8c8a0" stroke-width="1"/>' for x in range(6, 92, 6)],
    ], meta=[])


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    furnishings(sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "assets", "art", "decor"))
