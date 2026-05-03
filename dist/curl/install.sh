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
BUILD_DIR="./metatron"

echo -e "${BLUE}metatron ${YELLOW}installer"
echo -e "  ${BLUE}repo:${YELLOW}    ${REPO_URL}"
echo -e "  ${BLUE}install:${YELLOW} ${BUILD_DIR}${NC}"

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
      echo -e "${package]} installed"
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
    if [ "$JAVA_MAJOR_VERSION" -ge 21 ]; then
        echo -e "${CHECKMARK} java $JAVA_MAJOR_VERSION ${GREEN}already installed${NC}."
    else
        echo -e "java version $JAVA_MAJOR_VERSION ${RED}is too low${NC}. java 21 or higher is required."
        install_dependencies "openjdk-21-jdk"
    fi
fi

# Check for Maven
if ! command_exists mvn; then
    install_dependencies "maven"
else
    echo -e "${CHECKMARK} maven ${GREEN}already installed${NC}."
fi

# Clone the repository
echo -e "cloning repository from $REPO_URL..."
if [ -d "$BUILD_DIR" ]; then
    echo -e "directory $BUILD_DIR ${YELLOW}already exists${NC}. updating..."
    cd "$BUILD_DIR"
    git pull
else
    git clone "$REPO_URL"
    cd "$BUILD_DIR"
fi

# Build the project with Maven
echo -e "building project with maven..."
export MAVEN_OPTS=--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
# defaults
HOST="ws://0.0.0.0:8555"
BOOT="boot/boot.mtron"
LOG="info"
EXTRAS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --headless) export MTRON_HEADLESS=1       ; shift ;;
    --log)      LOG="$2"                     ; shift 2 ;;
    --host)     HOST="$2"                    ; shift 2 ;;
    --boot)     BOOT="$2"                    ; shift 2 ;;
    *)          break ;;
  esac
done

mvn compile exec:java -o -Dexec.args="[host=><${HOST}>,boot=><${BOOT}>,log=>${LOG}${EXTRAS}]"

# Check build status
if [ $? -eq 0 ]; then
    echo "build successful!"
else
    echo "build failed!"
    exit 1
fi