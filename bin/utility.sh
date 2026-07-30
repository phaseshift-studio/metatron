#!/usr/bin/env bash
#
# metatron: a distributed virtual machine and language
#  Copyright (C) 2025- PhaseShift Studio, LLC
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

# bin/utility.sh — ANSI colors, branding, and reusable shell functions.
# Source this file; do not execute directly.
#
# Usage:
#   . "$(dirname "$(readlink -f "$0")")/utility.sh"
#
# Color variables and PHASESHIFT are POSIX-compatible.
# spinner() requires bash (uses ${var:offset:length} substring expansion).

# -- ANSI color variables --
NC='\033[0m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
BOLDGREEN='\033[1;32m'
MAGENTA='\033[0;35m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'

# -- Semantic markers --
CHECKMARK="${GREEN}✅${NC}"
ERRORMARK="${RED}❌${NC}"

# -- Phaseshift Studio wordmark --
PHASESHIFT="${MAGENTA}PhaseS${GREEN}hift${BLUE}Studio${RED}${NC}"

# Bracket pair for framing labels: [ ... ]
LB="${MAGENTA}[${YELLOW}"
RB="${MAGENTA}]${NC}"

# -- Utility functions --

# Check if a command is available on the system.
# Usage: if command_exists java; then ...
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Display a braille spinner while a background process is running.
# Usage:
#   long_running_command &
#   spinner $! "Doing work..."
#
# Requires bash (uses ${var:offset:length} substring expansion).
spinner() {
    local pid=$1
    local message="${2:-Processing...}"
    local spin='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    local i=0

    # Hide cursor
    tput civis 2>/dev/null || true

    while kill -0 "$pid" 2>/dev/null; do
        i=$(( (i + 1) % 10 ))
        printf "\r${spin:$i:1} %s" "$message"
        sleep 0.1
    done

    # Show cursor and clear line
    tput cnorm 2>/dev/null || true
    printf "\r\033[K%b %s\n" "${GREEN}✓${NC}" "$message"
}
