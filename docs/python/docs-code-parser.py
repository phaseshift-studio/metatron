#  Metatron: A Distributed Computing Language and Virtual Machine
#  Copyright (C) 2025- PhaseShift Studio, LLC
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
mtron code runner for metatron documentation
"""
from __future__ import annotations

import argparse
import asyncio
import os
import random
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import TYPE_CHECKING, Any

from colors import *
from runner.mytron import Mytron, MtronSession

if TYPE_CHECKING:
    try:
        from typing import Literal  # type: ignore[attr-defined]
    except ImportError:
        from typing_extensions import Literal

if sys.version_info >= (3, 8):  # pragma: no cover
    from importlib.metadata import PackageNotFoundError, version

    # try:
    #    __version__ = version("docs-code-parser")
    # except PackageNotFoundError:
    __version__ = "3.9"
else:  # pragma: no cover

    __version__ = pkg_resources.get_distribution("markdown-code-runner").version

DEBUG: bool = os.environ.get("DEBUG", "0") == "1"

_CALLOUT_RE = re.compile(r'\s*\[-- <\d+>\s*$')


def remove_html_comment(commented_text: str) -> str:
    commented_text = commented_text.removesuffix(" -->")
    return commented_text.replace("<!-- ", "")


def _unroll_stream(s: str) -> list[str]:
    """Split a mtron stream result '{a,b,c}' into individual items.

    Uses a bracket-depth counter so commas inside rec[a=>b,c=>d] or
    nested streams are not treated as item separators.
    Returns a single-element list when the result is not a stream.
    """
    s = s.strip()
    if not (s.startswith("{") and s.endswith("}")):
        return [s]
    inner = s[1:-1]
    items: list[str] = []
    depth = 0
    buf: list[str] = []
    for ch in inner:
        if ch in "([{":
            depth += 1
            buf.append(ch)
        elif ch in ")]}":
            depth -= 1
            buf.append(ch)
        elif ch == "," and depth == 0:
            item = "".join(buf).strip()
            if item:
                items.append(item)
            buf = []
        else:
            buf.append(ch)
    last = "".join(buf).strip()
    if last:
        items.append(last)
    return items if items else [s]


def execute_code(
        code: list[str],
        context: dict[str, Any] | None = None,
        language: Literal["python", "bash"] = None,  # type: ignore[name-defined]
        *,
        output_file: str | Path | None = None,
        verbose: bool = False,
) -> list[str]:
    """Execute a code block and return its output as a list of strings."""
    if context is None:
        context = {}
    new_code = []
    cat = False
    for c in code:
        if not new_code:
            new_code.append(c)
        else:
            if cat:
                new_code.append(new_code.rstrip().removesuffix(" /\"") + "\n        " + c + "\"")
            else:
                new_code.append("\"" + c + "\"")
        stripped = _CALLOUT_RE.sub('', c.rstrip())
        cat = stripped.endswith("/")
    full_code = " ".join(new_code)
    full_code = full_code.replace("\\|", "|")
    # full_code = full_code.replace("{", "&<<")
    # full_code = full_code.replace("}", "&>>")

    if verbose:
        print(f"\n🐖 {BOLD}{GREEN}executing code {language} block:{NC}")
        print(f"\n{CYAN}{full_code}{NC}\n")

    if output_file is not None:
        output_file = Path(output_file)
        with output_file.open("w") as f:
            f.write(full_code)
        output = []
    elif language == "bash":
        result = subprocess.run(
            full_code,
            capture_output=True,
            text=True,
            shell=True,
        )
        output = result.stdout.split("\n")
    else:
        msg = "specify 'output_file' for non-Python/Bash languages."
        raise ValueError(msg)

    if verbose:
        print(f"\n🐓 {BOLD}{GREEN}output:{NC}")
        print(f"\n{CYAN}{output}\n{NC}")

    return output


def _bold(text: str) -> str:
    """Format a string as bold."""
    bold = "\033[1m"
    reset = "\033[0m"
    return f"{bold}{text}{reset}"


@dataclass
class ProcessingState:
    """State of the processing of an asciidoc file."""

    mtron: Mytron = field(default_factory=Mytron)
    section: Literal[
        "🐖",
        "👨‍🌾",
        "🐓",
        "X",
    ] = "👨‍🌾"
    code: list[str] = field(default_factory=list)
    context: dict[str, Any] = field(default_factory=dict)
    colors: list[str] = field(default_factory=dict)
    skip_code_block: bool = False
    output: list[str] | None = None
    new_lines: list[str] = field(default_factory=list)
    in_table: bool = False,
    backtick_options: dict[str, Any] = field(default_factory=dict)

    def random_color(self, to_color: str):
        index = random.randint(0, len(self.colors) - 1)
        color = self.colors[index]
        self.colors.remove(color)
        sep = "#" if bool(random.randint(0, 1)) else "*"
        return "[" + color + "]" + sep + to_color + sep  # + "​"

    def random_metatron(self):
        self.colors = ["red", "blue", "lime", "yellow", "fuchsia", "aqua", "green", "orange"]
        text = ""
        text += self.random_color("m" if bool(random.randint(0, 1)) else "M")
        text += self.random_color("e" if bool(random.randint(0, 1)) else "E")
        text += self.random_color("t" if bool(random.randint(0, 1)) else "T")
        text += self.random_color("a" if bool(random.randint(0, 1)) else "A")
        text += self.random_color("t" if bool(random.randint(0, 1)) else "T")
        text += self.random_color("r" if bool(random.randint(0, 1)) else "R")
        text += self.random_color("o" if bool(random.randint(0, 1)) else "O")
        text += self.random_color("n" if bool(random.randint(0, 1)) else "N")
        self.colors = []
        return text

    async def process_line(self, line: str, *, verbose: bool = False) -> None:
        """Process a line of the asciidoc file."""
        if self.section == "X":
            self.section = "👨‍🌾"
            return
        if line.strip().startswith("|==="):
            self.in_table = not self.in_table
        ################################################################################
        if (self.section == "👨‍🌾" and
                line.lstrip().startswith("<!--") and
                line.find("🐖") != -1 and "[SKIP]" not in line):
            self.section = "🐖"
            self.new_lines = self.new_lines[:-1]  # remove the ++++ wrapper
            self.code.append(remove_html_comment(line.strip()).replace("🐖", ""))
            if line.rstrip().endswith("-->"):
                await self._process_chicken_code(verbose=verbose)
                self._process_output_start(line)
                self.section = "🐓"
        elif self.section == "🐖":
            if line.lstrip() == "-->":
                await self._process_chicken_code(verbose=verbose)
                self._process_output_start("")
                self.section = "🐓"
            else:
                self.code.append(line)
        ############################################
        if self.section == "👨‍🌾":
            if -1 != line.find("[metatron]"):
                self.new_lines.append(line.replace("[metatron]", self.random_metatron()))
            else:
                self.new_lines.append(line)
        elif self.section == "🐓":
            self.new_lines.append("")
            self.section = "X"

    def _post_process_output(self, c: str, in_table: bool = True) -> str | None:
        if c.count("thrown at inst console") != 0:
            return None
        if (not in_table):
            # escape table separator character
            c = c.replace("\\|", "|")  # .replace("|", "\\|")
        # remove code=> frame reference as it's an artifact of the console.eval() remote code evaluation
        c = re.sub('code=>\'.*?\',', "", c)
        # fix source code callouts
        c = re.sub('\\[-- <(?P<a>[0-9]+)>', r'​\g<a>​', c)
        # c = re.sub('\\[-- <(?P<a>[0-9]+)>', r'\g<a>', c)
        return c

    def _process_output_start(self, line: str) -> None:
        assert isinstance(
            self.output,
            list,
        ), f"output must be a list, not {type(self.output)}, line: {line}"

        first_output = self.output[0] if self.output else ""
        pre_header = [] if "[NO_HEADER]" in first_output else [""]
        post_header = ["----"] if "[NO_HEADER]" in first_output else ["[source,mtron]", "----"]
        new_output = []
        new_header = []
        for c in self.output:
            if c.startswith("[HEADER] "):
                new_header.append(c.removeprefix("[HEADER] "))
            elif "[NO_HEADER]" not in c:
                o = self._post_process_output(c, self.in_table)
                if o is not None and o != "":
                    new_output.append(o)
        ###################################################################
        if not self.in_table:
            new_line = line.replace("\\|", "|")  # .replace("|", "\\|")  # .replace("&<<","{").replace("&>>","}")
            if new_line:
                self.new_lines.append(new_line)
        else:
            if line:
                self.new_lines.append(line)
        self.new_lines.extend(pre_header)
        self.new_lines.extend(new_header)
        self.new_lines.extend(post_header)
        self.new_lines.extend(new_output)
        # self.new_lines.pop()
        self.new_lines.extend(["----"])
        self.output = None  # Reset output after processing end of the output section

    async def _process_chicken_code(self, *, verbose: bool) -> None:
        to_header = []
        to_execute = []
        running_line = ""
        for line in self.code:
            stripped = _CALLOUT_RE.sub('', line.rstrip())
            if stripped.endswith("/"):
                running_line += stripped.removesuffix(
                    "/").rstrip() + "\n       "  # add spaces to shift right due to mtron> 
            elif line.startswith("[HEADER]"):
                to_header.append(line)
            else:
                to_execute.append(line if running_line == "" else running_line + line)
                running_line = ""
        self.output = []
        self.output.extend(to_header)
        result = []
        final_code = []
        current = ""
        for line in to_execute:
            current = current + line
            if not line.endswith("%"):
                if current.strip():  # skip blank lines left over from comment parsing
                    final_code.append(current)
                current = ""
        print(f"code: {final_code}")
        for line in final_code:
            if -1 == line.find("[HIDDEN]"):
                result.append(
                    f"mtron> {'\n       '.join(line.split("%"))}")  # the spaces are to shift right due to mtron> 
                for _item in _unroll_stream(await self.mtron.eval(line.replace('%', '').strip())):
                    result.append(f"==>{_item}")
                # result.append("​")
                # Clear accumulated failures after every visible eval so the catch()
                # response never grows large enough to trigger a timeout.
                # await self.mtron.eval_hidden("/sys/fail/+ -> noobj")
            elif line is not None:
                await self.mtron.eval(line.replace("%", "").replace("[HIDDEN]", "").strip())
        self.output.extend(result)
        print(self.output)
        self.code = []
        self.backtick_options = {}


async def process_asciidoc(
    content: list[str],
    prefix_content: list[str] | None = None,
    *,
    mtron: Mytron | None = None,
    verbose: bool = False,
) -> list[str]:
    """Process one asciidoc file, evaluating all 🐖 code blocks.

    Args:
        content:        Lines of the .adoc file to process.
        prefix_content: Optional lines whose code blocks run first (output discarded).
                        Use for [HIDDEN] setup that should apply to every file.
        mtron:          A connected Mytron instance to reuse.  If None, a new
                        connection is created and closed automatically.  Pass a
                        shared instance from MtronSession.native to avoid the
                        per-file connect/close overhead and to inherit a longer
                        timeout.
        verbose:        Enable line-by-line debug output.
    """
    assert isinstance(content, list), "input must be a list"

    owned = mtron is None
    state = ProcessingState(mtron=mtron if mtron is not None else Mytron())
    if owned:
        await state.mtron.connect()

    try:
        # Run prefix lines first (e.g. [HIDDEN] initialisation) and discard output.
        # Side-effects on the server are preserved across the shared connection.
        if prefix_content:
            for line in prefix_content:
                await state.process_line(line, verbose=verbose)
            state.new_lines = []

        for i, line in enumerate(content):
            if verbose:
                nr = _bold(f"line {i:4d}")
                print(f"{nr}: {line}")
            await state.process_line(line, verbose=verbose)
    finally:
        if owned:
            await state.mtron.close()

    return state.new_lines


async def update_asciidoc_file(
        input_filepath: Path | str,
        output_filepath: Path | str | None = None,
        *,
        verbose: bool = False,
        copy_only: bool = False,
        prefix: Path | str | None = None,
        timeout: int = 8,
) -> None:
    """Process all .adoc files in input_filepath, writing results to output_filepath.

    Opens a single MtronSession shared across every file so that connection
    overhead is paid only once.

    Args:
        timeout: Seconds to wait per eval.  30 s works for most expressions;
                 increase for runs that include LLM or other slow calls.
    """
    input_filepath  = Path(input_filepath)
    output_dir      = Path(output_filepath) if output_filepath is not None else input_filepath

    prefix_content: list[str] | None = None
    if prefix is not None:
        with Path(prefix).open() as pf:
            prefix_content = [line.rstrip("\n") for line in pf.readlines()]
        if verbose:
            print(f"prefix file: {prefix} ({len(prefix_content)} lines)")

    adoc_files = sorted(
        f for f in os.listdir(input_filepath)
        if os.path.isfile(os.path.join(input_filepath, f)) and f.endswith(".adoc")
    )

    if not adoc_files:
        print("no .adoc files found")
        return

    if copy_only:
        # Fast path — no metatron connection needed.
        for file in adoc_files:
            out_file = output_dir / os.path.basename(file)
            with (input_filepath / file).open() as f:
                content = f.read().rstrip()
            if verbose:
                print(f"copying {file} → {out_file}")
            out_file.write_text(content)
        if verbose:
            print("done (copy only)")
        return

    # One shared session for all files — timeout is configurable via the caller.
    session = MtronSession(timeout=timeout)
    await session.connect()
    print(f"[docs-runner] processing {len(adoc_files)} file(s) with shared connection (timeout={timeout}s)")

    try:
        for file in adoc_files:
            out_file = output_dir / os.path.basename(file)
            with (input_filepath / file).open() as f:
                original_lines = [line.rstrip("\n") for line in f.readlines()]
            if verbose:
                print(f"processing {file} → {out_file}")
            else:
                print(f"[docs-runner] {file}")

            new_lines = await process_asciidoc(
                original_lines,
                prefix_content,
                mtron=session.native,
                verbose=verbose,
            )
            out_file.write_text("\n".join(new_lines).rstrip())
    finally:
        await session.close()

    if verbose:
        print("done")


async def main() -> None:
    """Parse command line arguments and run the script."""
    parser = argparse.ArgumentParser(
        description="Automatically update asciidoc files with code block output.",
    )
    parser.add_argument(
        "input",
        type=str,
        help="Path to the input asciidoc file.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=str,
        help="Path to the output asciidoc file. (default: overwrite input file)",
        default=None,
    )
    parser.add_argument(
        "-d",
        "--verbose",
        action="store_true",
        help="Enable debugging mode (default: False)",
    )
    parser.add_argument(
        "-v",
        "--version",
        action="version",
        version=f"%(prog)s {__version__}",
    )
    parser.add_argument(
        "-c",
        "--copy_only",
        type=str,
        help="Copy input file to output file without processing (default: False)",
        default="False",
    )
    parser.add_argument(
        "-p",
        "--prefix",
        type=str,
        help="Path to an adoc file whose code blocks run before each input file (output discarded).",
        default=None,
    )
    parser.add_argument(
        "-t",
        "--timeout",
        type=int,
        default=8,
        help="Seconds to wait per mtron eval (default: 5). Increase for LLM or slow expressions.",
    )
    args = parser.parse_args()
    print("\n[Docs Runner v0.224-db-a345c3456.3342323]\n\targs: ", args)
    input_filepath = Path(args.input)
    output_filepath = Path(args.output) if args.output is not None else input_filepath
    print(f"{input_filepath} => {output_filepath}")
    await update_asciidoc_file(input_filepath, output_filepath, verbose=args.verbose,
                               copy_only=args.copy_only == 'true', prefix=args.prefix,
                               timeout=args.timeout)


if __name__ == "__main__":
    asyncio.run(main())
