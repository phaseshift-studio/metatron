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


def string(s: str, *args):
    return (s.
            replace("{{y}}", YELLOW).
            replace("{{m}}", MAGENTA).
            replace("{{c}}", CYAN).
            replace("{{b}}", BLUE).
            replace("{{r}}", RED).
            replace("{{g}}", GREEN).
            replace("{{k}}", BLACK).
            replace("{{w}}", WHITE).
            replace("{{X}}", NC).
            replace("{{~}}", ITALIC)).format(*args)


class LOG:
    @staticmethod
    def info(s: str, *args):
        print(f"[{GREEN}INFO {NC}] {string(s, *args)}{NC}")

    @staticmethod
    def debug(s: str, *args):
        print(f"[{YELLOW}DEBUG{NC}] {string(s, *args)}{NC}")

    @staticmethod
    def error(s: str, *args):
        print(f"[{RED}ERROR{NC}] {string(s, *args)}{NC}")

    @staticmethod
    def warn(s: str, *args):
        print(f"[{YELLOW}WARN {NC}] {string(s, *args)}{NC}")

    @staticmethod
    def none(s: str, *args):
        print(f"{string(s, *args)}{NC}")
