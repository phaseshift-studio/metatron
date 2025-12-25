""" ANSI color codes """
BLACK = '\u001b[0;30m'
RED = '\u001b[0;31m'
GREEN = '\u001b[0;32m'
BROWN = '\u001b[0;33m'
BLUE = '\u001b[0;34m'
MAGENTA = '\u001b[0;35m'
CYAN = '\u001b[0;36m'
LIGHT_GRAY = '\u001b[0;37m'
DARK_GRAY = '\u001b[1;30m'
LIGHT_RED = '\u001b[1;31m'
LIGHT_GREEN = '\u001b[1;32m'
YELLOW = '\u001b[1;33m'
LIGHT_BLUE = '\u001b[1;34m'
LIGHT_PURPLE = '\u001b[1;35m'
# LIGHT_CYAN = "\u001b[1;36m"
WHITE = "\u001b[1;37m"
BOLD = '\u001b[1m'
# FAINT = "\u001b[2m"
ITALIC = "\u001b[3m"
# UNDERLINE = "\u001b[4m"
# BLINK = "\u001b[5m"
# NEGATIVE = "\u001b[7m"
# CROSSED = "\u001b[9m"
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

class LOG:
    @staticmethod
    def _generic_log(level: str, color: str, s: str, *args):
        print(f"[{color}{level} {NC}] {string(s, *args)}{NC}")
        if log_behavior is not None: log_behavior(level, s, *args)

    @staticmethod
    def info(s: str, *args):
        LOG._generic_log("INFO", GREEN, s, *args)

    @staticmethod
    def debug(s: str, *args):
        LOG._generic_log("DEBUG", CYAN, s, *args)

    @staticmethod
    def error(s: str, *args):
        LOG._generic_log("ERROR", RED, s, *args)

    @staticmethod
    def warn(s: str, *args):
        LOG._generic_log("WARN", YELLOW, s, *args)

    @staticmethod
    def none(s: str, *args):
        print(f"{string(s, *args)}{NC}")
