#!/usr/bin/env python3
"""Render the article TikZ sidecars to SVG.

A sidecar (`docs/website/articles/images/<article>/fig-NN.tex`) holds one figure's
tikz-cd source *verbatim* — exactly the LaTeX the article carried inline before the
figure became an image. That is what the PDF surface reads back (see
`docs/website/articles/tikz.lua`), so the sidecar must stay verbatim. Rendering it
therefore wraps it into a standalone document first:

  * `\\begin{equation*}`/`\\end{equation*}` become inline math, because the standalone
    class typesets its body in an hbox where display math is illegal
    ("Bad math environment delimiter");
  * the expanded `\\end{tikzcd}` tail the article was pasted with
    (`\\pgfmatrixendrow\\egroup;` / `\\tikzcd@finalize%` / `\\endtikzpicture`) is
    restored to the `\\end{tikzcd}` it expands from.

The page is left unpainted and the ink is `--ink` (white by default), because the
website is dark red and black: black line art is invisible on it, and painting the page
would put a plate behind every diagram. `\\tikzset{every node/…{fill=none}}` keeps arrow
labels unbacked for the same reason — nothing opaque sits between the ink and the page.
The PDF surface is untouched by any of this: it reads the sidecar, not the SVG.

    bin/render-tikz.py [dir ...]     # default: docs/website/articles/images
    bin/render-tikz.py --png …       # also write a raster fallback beside each svg
    bin/render-tikz.py --ink black … # for a light page (white ink is invisible on one)
    bin/render-tikz.py --scale 1 …   # svg at print size (default: 2.78, see below)
    bin/render-tikz.py --check …     # exit 1 if a committed svg is out of date

pdftocairo writes the svg at *print* size — one user unit per TeX point — so a
`row sep=tiny` diagram lands ~60px wide with ~10px labels, unreadable on screen. The
figures are vector, so the svg's width/height are rescaled (the viewBox is untouched,
so it stays lossless) to 200/72 = 2.78x by default: the size of the 200-dpi raster
preview these figures were checked against. `--scale 1` restores print size. The PDF
is unaffected either way — it reads the sidecar, not the svg.

`pdflatex` + `pdftocairo` are used directly when they are on PATH; otherwise the same
commands run inside a TeX container (`TIKZ_TEX_IMAGE`, default `tikz-mcp:local` — the
image this deployment ships for TikZ rendering; any image with texlive + poppler will
do).
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DEFAULT_DIRS = ['docs/website/articles/images']
PREAMBLE = r"""\documentclass[border=4pt]{standalone}
\usepackage{tikz}
\usepackage{tikz-cd}
\usepackage{amsmath,amssymb}
\usepackage{xcolor}
\begin{document}
\color{%INK%}%
\tikzset{every node/.append style={fill=none}}%
"""
EXPANDED_TAIL = re.compile(
    r'(?m)^[ \t]*%[ \t]*\n'
    r'[ \t]*\\pgfmatrixendrow\\egroup;%[ \t]*\n'
    r'[ \t]*\\tikzcd@finalize%[ \t]*\n'
    r'([ \t]*)\\endtikzpicture')


def standalone(tex: str, ink: str) -> str:
    """Wrap a verbatim sidecar into a document the standalone class can typeset."""
    body, tails = EXPANDED_TAIL.subn(lambda m: m.group(1) + '\\end{tikzcd}', tex)
    opens = body.count('\\begin{equation*}')
    assert opens == body.count('\\end{equation*}'), "unbalanced equation* in sidecar"
    body = body.replace('\\begin{equation*}', '$').replace('\\end{equation*}', '$')
    return PREAMBLE.replace('%INK%', ink) + body.strip('\n') + '\n\\end{document}\n'


def host_repo() -> Path:
    """The repo path as the DOCKER DAEMON sees it (for -v), not as we see it here."""
    override = os.environ.get('METATRON_HOST_REPO')
    if override:
        return Path(override)
    repo = Path.cwd().resolve()
    try:
        mountinfo = Path('/proc/self/mountinfo').read_text()
    except OSError:
        return repo
    best = None
    for line in mountinfo.splitlines():
        fields = line.split()
        if len(fields) > 4 and (repo == Path(fields[4]) or repo.is_relative_to(Path(fields[4]))):
            if best is None or len(fields[4]) > len(best[0]):
                best = (fields[4], fields[3])
    if best is None:
        return repo
    mount, root = best
    return Path(root) / repo.relative_to(mount)


def shell(script: str, build: Path) -> subprocess.CompletedProcess:
    local = shutil.which('pdflatex') and shutil.which('pdftocairo')
    if local:
        return subprocess.run(['sh', '-c', script], cwd=build, capture_output=True, text=True)
    image = os.environ.get('TIKZ_TEX_IMAGE', 'tikz-mcp:local')
    docker = shutil.which('docker') or '/opt/dsh-tools/bin/docker'
    if not Path(docker).exists():
        sys.exit('render-tikz: no pdflatex/pdftocairo on PATH and no docker CLI — '
                 'install texlive + poppler, or set TIKZ_TEX_IMAGE to a TeX image')
    host = host_repo()
    return subprocess.run(
        [docker, 'run', '--rm', '-v', f'{host}:{host}', '-w', str(host / build.relative_to(Path.cwd())),
         '-u', f'{os.getuid()}:{os.getgid()}', '-e', 'HOME=/tmp', image, 'sh', '-c', script],
        capture_output=True, text=True)


def render(sidecars, build: Path, png: bool, ink: str):
    build.mkdir(parents=True, exist_ok=True)
    for f in build.iterdir():
        if f.is_file():
            f.unlink()
    for sidecar in sidecars:
        (build / sidecar.with_suffix('.tex').name).write_text(
            standalone(sidecar.read_text(encoding='utf-8'), ink), encoding='utf-8')
    script = ('set -e\n'
              'for f in *.tex; do b=${f%.tex}\n'
              '  pdflatex -interaction=nonstopmode -halt-on-error "$f" > "$b.log" 2>&1\n'
              '  pdftocairo -svg "$b.pdf" "$b.svg"\n'
              # -transp matters as much as the ink colour: a white-on-white raster
              # preview is blank, so the PNG keeps the page transparent too
              + ('  pdftocairo -png -transp -r 200 -singlefile "$b.pdf" "$b"\n' if png else '')
              + 'done\n')
    result = shell(script, build)
    if result.returncode != 0:
        for log in sorted(build.glob('*.log')):
            errors = [l for l in log.read_text(errors='replace').splitlines() if l.startswith('!')]
            if errors:
                print(f'{log.stem}: {errors[0]}', file=sys.stderr)
        sys.exit(f'render-tikz: pdflatex failed\n{result.stdout[-2000:]}\n{result.stderr[-2000:]}')


def scale_svg(path: Path, scale: float) -> None:
    """Rescale the root svg's width/height, leaving the viewBox (so: losslessly)."""
    if scale == 1:
        return
    text = path.read_text(encoding='utf-8')
    # the first width/height in the document are the root <svg> element's
    scaled = re.sub(r'\b(width|height)="([\d.]+)(?:pt)?"',
                    lambda m: f'{m.group(1)}="{float(m.group(2)) * scale:g}"',
                    text, count=2)
    path.write_text(scaled, encoding='utf-8')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('dirs', nargs='*', default=DEFAULT_DIRS,
                        help=f'sidecar directories (default: {" ".join(DEFAULT_DIRS)})')
    parser.add_argument('--png', action='store_true', help='also write a raster fallback')
    parser.add_argument('--ink', default='white',
                        help='diagram ink colour (default white: the site is dark red and '
                             'black, so black line art is invisible); the page itself is '
                             'never painted, so the background stays transparent')
    parser.add_argument('--scale', type=float, default=200 / 72,
                        help='svg display scale (default 2.78 = 200dpi; 1 = print size)')
    parser.add_argument('--check', action='store_true',
                        help='do not write; fail when a committed svg differs from a fresh render')
    args = parser.parse_args()

    sidecars = sorted(p for d in args.dirs for p in Path(d).rglob('*.tex'))
    if not sidecars:
        sys.exit(f'render-tikz: no .tex sidecars under {", ".join(args.dirs)}')

    build = Path(tempfile.mkdtemp(prefix='render-tikz.', dir=Path.cwd() / 'target'))
    try:
        render(sidecars, build, args.png, args.ink)
        for sidecar in sidecars:
            scale_svg(build / sidecar.with_suffix('.svg').name, args.scale)
        stale, written = [], 0
        for sidecar in sidecars:
            for ext in (['.svg', '.png'] if args.png else ['.svg']):
                fresh = build / sidecar.with_suffix(ext).name
                committed = sidecar.with_suffix(ext)
                if not fresh.exists():
                    stale.append(str(committed))
                    continue
                if args.check:
                    if not committed.exists() or committed.read_bytes() != fresh.read_bytes():
                        stale.append(str(committed))
                else:
                    shutil.copy2(fresh, committed)
                    written += 1
        if args.check:
            if stale:
                print('out of date:\n  ' + '\n  '.join(stale))
                return 1
            print(f'{len(sidecars)} figure(s) up to date')
        else:
            print(f'rendered {len(sidecars)} figure(s) -> {written} file(s)')
    finally:
        shutil.rmtree(build, ignore_errors=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
