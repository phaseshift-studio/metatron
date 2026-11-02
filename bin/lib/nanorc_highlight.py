#  metatron: a distributed virtual machine and language
#   Copyright (C) 2025- PhaseShift Studio, LLC
# 
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Affero General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
# 
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Affero General Public License for more details.
# 
#  You should have received a copy of the GNU Affero General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.

"""
nanorc_highlight.py
Parses nanorc files (compatible with JLine3's SyntaxHighlighter)
and applies regex-based syntax highlighting to terminal output.
"""

import re
from pathlib import Path

# --- Color mapping ---

ANSI_COLORS = {
    'black': 30, 'red': 31, 'green': 32, 'yellow': 33,
    'blue': 34, 'magenta': 35, 'cyan': 36, 'white': 37,
    'brightblack': 90, 'brightred': 91, 'brightgreen': 92,
    'brightyellow': 93, 'brightblue': 94, 'brightmagenta': 95,
    'brightcyan': 96, 'brightwhite': 97,
    # nano 256-color names
    'pink': '38;5;204', 'purple': '38;5;129', 'mauve': '38;5;176',
    'lagoon': '38;5;72', 'mint': '38;5;48', 'lime': '38;5;46',
    'peach': '38;5;216', 'orange': '38;5;208', 'latte': '38;5;223',
    'rosy': '38;5;211', 'beet': '38;5;125', 'plum': '38;5;133',
    'sea': '38;5;73', 'sky': '38;5;39', 'slate': '38;5;66',
    'teal': '38;5;36', 'sage': '38;5;107', 'brown': '38;5;130',
    'ocher': '38;5;178', 'sand': '38;5;222', 'tawny': '38;5;172',
    'brick': '38;5;124', 'crimson': '38;5;196',
}

STYLES = {
    'bold': '1', 'italic': '3', 'underline': '4',
    'dim': '2', 'blink': '5', 'reverse': '7', 'hidden': '8',
}

RESET = '\033[0m'


def color_to_ansi(color: str) -> str:
    """Convert a nanorc/JLine color spec to an ANSI escape code."""
    if not color or color in ('normal', 'default'):
        return ''

    # True color: #RRGGBB
    if color.startswith('#') and len(color) == 7:
        r, g, b = int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)
        return f'\033[38;2;{r};{g};{b}m'

    # 256-color by number: ~name or bare number
    if color.startswith('~'):
        return f'\033[38;5;{color[1:]}m'
    if color.isdigit():
        return f'\033[38;5;{color}m'

    # Named color (with optional bright/light prefix)
    name = color.lower()
    if name in ('bright', 'light'):
        return '\033[1m'  # just bold
    if name in ANSI_COLORS:
        code = ANSI_COLORS[name]
        if ';' in str(code):  # 256-color
            return f'\033[{code}m'
        return f'\033[{code}m'

    # JLine style names
    if name in STYLES:
        return f'\033[{STYLES[name]}m'

    return ''


def bg_color_to_ansi(color: str) -> str:
    """Convert a nanorc background color to ANSI."""
    if not color or color in ('normal', 'default'):
        return ''
    if color.startswith('#') and len(color) == 7:
        r, g, b = int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)
        return f'\033[48;2;{r};{g};{b}m'
    if color.startswith('~'):
        return f'\033[48;5;{color[1:]}m'
    if color.isdigit():
        return f'\033[48;5;{color}m'
    # Map to bg codes
    bg_map = {k: v + 10 for k, v in ANSI_COLORS.items() if isinstance(v, int) and v < 100}
    name = color.lower()
    if name in bg_map:
        return f'\033[{bg_map[name]}m'
    return ''


# --- Regex translation ---

POSIX_CLASSES = {
    'space': r'\s',
    'blank': r'[ \t]',
    'alnum': r'\w',
    'alpha': r'[a-zA-Z]',
    'digit': r'[0-9]',
    'xdigit': r'[0-9a-fA-F]',
    'upper': r'[A-Z]',
    'lower': r'[a-z]',
    'punct': r'[!-/:-@[-`{-~]',
    'graph': r'[-!-~]',
    'print': r'[- ~]',
    'word': r'\w',  # JLine extension
}


def translate_regex(pattern: str) -> str:
    """Translate GNU/POSIX regex extensions to Python re."""

    # Replace ALL [[:class:]] with a single regex pass
    def _replace_posix(m):
        cls = m.group(1).lower()
        return POSIX_CLASSES.get(cls, m.group(0))  # leave unknown as-is

    pattern = re.sub(r'\[:(\w+):\]', _replace_posix, pattern)

    # GNU word boundaries
    pattern = pattern.replace(r'\<', r'\b').replace(r'\>', r'\b').replace('[[]', '[\\[')

    return pattern


# --- Parser ---

class Rule:
    def __init__(self, fg: str, bg: str, patterns: list, case_insensitive: bool = False,
                 start: str = None, end: str = None):
        self.fg = fg
        self.bg = bg
        self.patterns = patterns  # list of raw regex strings
        self.case_insensitive = case_insensitive
        self.start = start
        self.end = end
        self.ansi_fg = color_to_ansi(fg)
        self.ansi_bg = bg_color_to_ansi(bg)
        self.flags = re.IGNORECASE if case_insensitive else 0

    def prefix(self) -> str:
        return self.ansi_fg + self.ansi_bg

    def compiled(self) -> list:
        return [re.compile(translate_regex(p), self.flags) for p in self.patterns]


class Syntax:
    def __init__(self, name: str, file_pattern: str):
        self.name = name
        self.file_pattern = file_pattern
        self.rules: list[Rule] = []


def parse_nanorc(path: str | Path) -> list[Syntax]:
    """Parse a nanorc file, handling include directives recursively."""
    path = Path(path).expanduser()
    if not path.exists():
        raise FileNotFoundError(f"nanorc file not found: {path}")

    syntaxes = []
    current: Syntax | None = None

    with open(path) as f:
        for raw_line in f:
            line = raw_line.strip()
            if not line or line.startswith('#'):
                continue

            # Handle include
            if line.startswith('include '):
                include_path = line[len('include '):].strip().strip('"')
                include_path = Path(include_path).expanduser()
                if not include_path.is_absolute():
                    include_path = path.parent / include_path
                syntaxes.extend(parse_nanorc(include_path))
                continue

            # Handle syntax definition
            m = re.match(r'syntax\s+"?([^"\s]+)"?\s+"(.+)"', line)
            if m:
                current = Syntax(m.group(1), m.group(2))
                syntaxes.append(current)
                continue

            # Handle color / icolor
            m = re.match(r'(i?color)\s+([^\s]+)\s+(.*)', line)
            if m and current:
                case_insensitive = (m.group(1) == 'icolor')
                colors_spec = m.group(2)
                rest = m.group(3)

                # Parse fg,bg
                if ',' in colors_spec:
                    fg, bg = colors_spec.split(',', 1)
                else:
                    fg, bg = colors_spec, ''

                # Check for start/end (multi-line)
                start_m = re.search(r'start="([^"]*)"\s+end="([^"]*)"', rest)
                if start_m:
                    rule = Rule(fg, bg, [], case_insensitive,
                                start=start_m.group(1), end=start_m.group(2))
                    current.rules.append(rule)
                    continue

                # Extract all quoted patterns
                patterns = re.findall(r'"((?:[^"\\]|\\.)*)"', rest)
                if patterns:
                    rule = Rule(fg, bg, patterns, case_insensitive)
                    current.rules.append(rule)

    return syntaxes


# --- Highlighter ---

class NanorcHighlighter:
    def __init__(self, syntax: Syntax):
        self.syntax = syntax
        self._compiled = [(rule, rule.compiled()) for rule in syntax.rules
                          if not rule.start]  # single-line rules only

    def highlight_block(self, text: str) -> str:
        """Highlight a block of text (line by line for simplicity)."""
        lines = text.split('\n')
        return '\n'.join(self.highlight(line) for line in lines)

    def highlight(self, text: str) -> str:
        n = len(text)
        if n == 0:
            return text

        # claimed[i] = index into self._compiled, or None
        claimed = [None] * n

        # Apply rules in file order — later rules overwrite earlier ones
        for rule_idx, (rule, compiled) in enumerate(self._compiled):
            for pattern in compiled:
                for m in pattern.finditer(text):
                    for i in range(m.start(), m.end()):
                        claimed[i] = rule_idx

        # Build output from the final position→rule map
        result = []
        i = 0
        while i < n:
            if claimed[i] is None:
                j = i
                while j < n and claimed[j] is None:
                    j += 1
                result.append(text[i:j])
                i = j
            else:
                rule = self._compiled[claimed[i]][0]
                j = i
                while j < n and claimed[j] == claimed[i]:
                    j += 1
                result.append(rule.prefix())
                result.append(text[i:j])
                result.append(RESET)
                i = j
        return ''.join(result)


# --- Convenience ---

def load_highlighter(nanorc_path: str | Path, syntax_name: str) -> NanorcHighlighter:
    """Load a nanorc file and return a highlighter for the named syntax."""
    syntaxes = parse_nanorc(nanorc_path)
    for s in syntaxes:
        if s.name.lower() == syntax_name.lower():
            return NanorcHighlighter(s)
    available = [s.name for s in syntaxes]
    raise ValueError(f"Syntax '{syntax_name}' not found. Available: {available}")
