#!/bin/sh
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

# bin/color.sh — ANSI color variables sourced by metatron shell scripts.
# Do not execute directly.

NC='\033[0m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
BOLDGREEN='\033[1;32m'
MAGENTA='\033[0;35m'
BLUE='\033[0;34m'

# Phaseshift Studio wordmark
PHASESHIFT="${MAGENTA}PhaseS${GREEN}hift${BLUE}Studio${RED}${NC}"

# Bracket pair for framing labels: [ ... ]
LB="${MAGENTA}[${YELLOW}"
RB="${MAGENTA}]${NC}"
