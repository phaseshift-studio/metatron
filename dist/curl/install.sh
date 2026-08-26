#!/usr/bin/env bash
set -e

# Define colors
BLACK='\e[0;30m'
RED='\e[0;31m'
GREEN='\e[0;32m'
YELLOW='\e[0;33m'
BLUE='\e[0;34m'
PURPLE='\e[0;35m'
CYAN='\e[0;36m'
WHITE='\e[0;37m'
NC='\e[0m'  # No Color / Reset
CHECKMARK="${GREEN}\u2705"
ERRORMARK="${RED}\u274C"

# Configuration
REPO_URL="https://github.com/phaseshift-studio/metatron.git"
BUILD_DIR="${PWD}/metatron"

HEADER="${RED}                _        _                   
 _ __ ___   ___| |_ __ _| |_ _ __ ___  _ __  
| '_  ${YELLOW} _ \ / _ \ __/ _  | __| '__/ _ \| '_ \ 
| | | | | |  __/ || (_| | |_| | | (_) | | | |
|_| |_| |_|\___|\__${GREEN}\__,_|\__|_|  \___/|_| |_|
                            ${BLUE}PhaseShift Studio${NC}"

echo -e "$HEADER"
echo -e ""
echo -e "repository:  ${REPO_URL}"
echo -e "install dir: ${BUILD_DIR}"
echo -e ""

# loading icon
spinner() {
    local pid=$1
    local message="${2:-Processing...}"
    local spin='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    local i=0

    # Hide cursor
    tput civis 2>/dev/null || true

    while kill -0 "$pid" 2>/dev/null; do
        i=$(( (i + 1) % 10 ))
        printf "\r${spin:$i:1} $message"
        sleep 0.1
    done

    # Show cursor and clear line
    tput cnorm 2>/dev/null || true
    printf "\r\033[K✓ $message\n"
}


# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to install Java 21 and Maven
install_dependencies() {
    local package="$1"
    echo -e "${package} ${RED}not installed${NC}"
    echo -e "  it is recommended that the user install ${package} manually"
    read -p "would you like to have ${package} installed automatically now? (y/n): " confirm
    if [[ "$confirm" =~ ^[Yy]$ ]]; then
      echo -e "installing ${package}..."
      sudo apt-get update
      sudo apt-get install -y ${package}
      echo -e "${package} installed"
    else
      echo "installation cancelled by user"
      exit 1
    fi
}

# Check for Java 21
if ! command_exists java; then
    install_dependencies "openjdk-21-jdk"
else
    # Extract major version (handles both 1.x and x formats)
    JAVA_MAJOR_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {split($2, a, "."); gsub(/[^0-9]/, "", a[1]); print a[1]}')
    # Check if version is 21 or higher
    if [ "${JAVA_MAJOR_VERSION}" -ge 21 ]; then
        echo -e "${CHECKMARK} java ${JAVA_MAJOR_VERSION} ${GREEN}already installed${NC}"
    else
        echo -e "java version ${JAVA_MAJOR_VERSION} ${RED}is too low${NC}. java 21 or higher is required."
        install_dependencies "openjdk-21-jdk"
    fi
fi

# Check for Maven
if ! command_exists mvn; then
    install_dependencies "maven"
else
    echo -e "${CHECKMARK} maven ${GREEN}already installed${NC}"
fi

# patchelf: the jar ships a musl-built tree-sitter native lib; on glibc Linux,
# ObjJavaSerializer patches it with patchelf (missing → tree-sitter features fail).
# Non-fatal: if patchelf can't be installed, metatron still runs — only Java
# source parsing degrades.
if ! command_exists patchelf; then
    echo -e "${YELLOW}patchelf not installed — installing (tree-sitter native lib needs it on glibc)${NC}"
    sudo apt-get install -y patchelf || echo -e "${YELLOW}warning: could not install patchelf; Java source parsing may fail${NC}"
else
    echo -e "${CHECKMARK} patchelf ${GREEN}already installed${NC}"
fi

# Clone the repository
echo -e "cloning ${REPO_URL}..."
if [ -d "${BUILD_DIR}" ]; then
    echo -e "directory ${BUILD_DIR} ${YELLOW}already exists${NC}"
    echo -e "updating..."
    cd "${BUILD_DIR}"
    git pull
else
    git clone "${REPO_URL}"
    cd "${BUILD_DIR}"
fi

# Build the project with Maven
export MAVEN_OPTS="--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
mvn clean install -q -DskipTests=true &
MVN_PID=$!
spinner "$MVN_PID" "installing metatron"
# `set -e` would abort on a failed wait; capture the real build status instead.
set +e
wait "$MVN_PID"
MVN_EXIT=$?
set -e

# Check build status
if [ "$MVN_EXIT" -eq 0 ]; then
    # Bundle the uber-jar for runtime: bin/metatron launches lib/metatron.jar
    # when present (no Maven needed at runtime).
    mkdir -p lib
    UBER_JAR=$(ls target/metatron-*-jar-with-dependencies.jar 2>/dev/null | head -1)
    if [ -n "$UBER_JAR" ]; then
        cp "$UBER_JAR" lib/metatron.jar
        echo -e "${CHECKMARK} bundled ${UBER_JAR##*/} -> lib/metatron.jar${NC}"
    else
        echo -e "${YELLOW}warning: uber-jar not found; bin/metatron will need maven at runtime${NC}"
    fi
    echo -e "${CHECKMARK} build successful${NC}"
    echo -e "${GREEN}up next${NC}"
    echo "cd ${BUILD_DIR}"
    echo "bin/metatron --help"
else
    echo -e "${RED} build failed${NC}"
    exit 1
fi
