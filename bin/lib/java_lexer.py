"""java_lexer — a Java source, line by line, with its comments taken out.

Shared by bin/hunk-revert-check (does a hunk change anything but comments, whitespace or imports?) and
bin/test-select (a test class's declaration: abstract or not, what it extends). It reads the whole file in one pass,
so a line inside a block comment that started earlier is known to be comment, and it knows the literals that hide
comment markers: "http://x" is a string, '"' is a char, and a text block ends at its closing triple quote only,
past any escaped quote inside it. Unicode escapes (\\u002F) are not decoded.
"""
import re
from typing import NamedTuple


class Line(NamedTuple):
    code: str  # the line with comments removed and whitespace outside literals removed
    text: str  # the line with comments removed (a comment between tokens leaves one space)


IMPORT_LINE = re.compile(r'^\s*import\s+(static\s+)?[\w$]+(\s*\.\s*[\w$]+)*(\s*\.\s*\*)?\s*;\s*$')

_CODE, _LINE_COMMENT, _BLOCK_COMMENT, _STRING, _CHAR, _TEXT_BLOCK = range(6)


def lex(source: str) -> list[Line]:
    """One Line per line of source (split at '\\n', as git counts lines)."""
    lines: list[Line] = []
    code: list[str] = []
    text: list[str] = []
    state = _CODE
    i, n = 0, len(source)
    while i < n:
        c = source[i]
        if c == '\n':
            lines.append(Line(''.join(code), ''.join(text)))
            code, text = [], []
            if state in (_LINE_COMMENT, _STRING, _CHAR):  # neither a string nor a char spans lines
                state = _CODE
            i += 1
            continue
        if state == _CODE:
            if source.startswith('//', i):
                state = _LINE_COMMENT
                i += 2
                continue
            if source.startswith('/*', i):
                state = _BLOCK_COMMENT
                text.append(' ')
                i += 2
                continue
            if source.startswith('"""', i):
                state = _TEXT_BLOCK
                code.append('"""')
                text.append('"""')
                i += 3
                continue
            if c == '"':
                state = _STRING
            elif c == "'":
                state = _CHAR
            text.append(c)
            if not c.isspace():
                code.append(c)
            i += 1
            continue
        if state == _LINE_COMMENT:
            i += 1
            continue
        if state == _BLOCK_COMMENT:
            if source.startswith('*/', i):
                state = _CODE
                i += 2
            else:
                i += 1
            continue
        # inside a literal: an escape takes the next character with it, whatever it is
        if c == '\\' and i + 1 < n and source[i + 1] != '\n':
            code.append(source[i:i + 2])
            text.append(source[i:i + 2])
            i += 2
            continue
        if state == _TEXT_BLOCK and source.startswith('"""', i):
            code.append('"""')
            text.append('"""')
            state = _CODE
            i += 3
            continue
        if (state == _STRING and c == '"') or (state == _CHAR and c == "'"):
            state = _CODE
        code.append(c)
        text.append(c)
        i += 1
    lines.append(Line(''.join(code), ''.join(text)))
    return lines
