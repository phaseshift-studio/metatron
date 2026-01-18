""" ANSI color codes """
BLACK = '\u001b[0;30m'
RED = '\u001b[0;31m'
GREEN = '\u001b[0;32m'
BLUE = '\u001b[0;34m'
MAGENTA = '\u001b[0;35m'
CYAN = '\u001b[0;36m'
YELLOW = '\u001b[1;33m'
WHITE = '\u001b[1;37m'
BOLD = '\u001b[1m'
ITALIC = "\u001b[3m"
UNDERLINE = "\u001b[4m"
CROSSED = "\u001b[9m"
NC = '\u001b[0m'


def string(s: str, *args) -> str:
    s = s.replace("{{y}}", YELLOW)
    s = s.replace("{{m}}", MAGENTA)
    s = s.replace("{{c}}", CYAN)
    s = s.replace("{{b}}", BLUE)
    s = s.replace("{{r}}", RED)
    s = s.replace("{{g}}", GREEN)
    s = s.replace("{{k}}", BLACK)
    s = s.replace("{{w}}", WHITE)
    s = s.replace("{{X}}", NC)
    s = s.replace("{{~}}", ITALIC)
    s = s.format(*args)
    return s


def strip(s: str) -> str:
    s = s.replace(YELLOW, "")
    s = s.replace(MAGENTA, "")
    s = s.replace(CYAN, "")
    s = s.replace(BLUE, "")
    s = s.replace(RED, "")
    s = s.replace(GREEN, "")
    s = s.replace(BLACK, "")
    s = s.replace(WHITE, "")
    s = s.replace(NC, "")
    s = s.replace(ITALIC, "")
    return s


log_behavior = None
log_level = "info"


#  Metatron: A Distributed Computing Language and Virtual Machine
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

class LOG:
    levels = ["trace", "debug", "info", "warn", "error"]

    @staticmethod
    def _generic_log(level: str, color: str, s: str, *args):
        if LOG.levels.index(log_level) <= LOG.levels.index(level.rstrip().lower()):
            print(f"[{color}{level}{NC}] {string(s, *args)}{NC}")
            if log_behavior is not None: log_behavior(level, s, *args)

    @staticmethod
    def info(s: str, *args):
        LOG._generic_log("INFO ", GREEN, s, *args)

    @staticmethod
    def debug(s: str, *args):
        LOG._generic_log("DEBUG", CYAN, s, *args)

    @staticmethod
    def error(s: str, *args):
        LOG._generic_log("ERROR", RED, s, *args)

    @staticmethod
    def warn(s: str, *args):
        LOG._generic_log("WARN ", YELLOW, s, *args)

    @staticmethod
    def none(s: str, *args):
        print(f"{string(s, *args)}{NC}")
