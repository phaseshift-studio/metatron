#!/usr/bin/env bash
set -e

# Colors
BLACK='\e[0;30m'
RED='\e[0;31m'
GREEN='\e[0;32m'
YELLOW='\e[0;33m'
BLUE='\e[0;34m'
PURPLE='\e[0;35m'
CYAN='\e[0;36m'
WHITE='\e[0;37m'
NC='\e[0m'

REPO="phaseshift-studio/metatron"

# Detect OS
case "$(uname -s)" in
  Linux)  OS="linux" ;;
  Darwin) OS="mac" ;;
  CYGWIN*|MINGW*|MSYS*) OS="win" ;;
  *)      echo "Unsupported OS: $(uname -s)"; exit 1 ;;
esac

# Detect arch
ARCH=$(uname -m)
case "$ARCH" in
  x86_64|amd64)  ARCH="x64" ;;
  aarch64|arm64) ARCH="arm64" ;;
  *)             echo "Unsupported arch: $ARCH"; exit 1 ;;
esac

echo -e "${BLUE}metatron-vm ${YELLOW}installer${NC}"
echo -e "  ${BLUE}platform:${YELLOW} ${OS}-${ARCH}${NC}"

# Get latest release tag (snapshot or stable depending on channel)
CHANNEL="${1:-snapshot}"
if [ "$CHANNEL" = "stable" ]; then
  TAG=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | grep '"tag_name"' | head -1 | cut -d'"' -f4)
  if [ -z "$TAG" ]; then
    echo "No stable release found"
    exit 1
  fi
else
  TAG="jdeploy-snapshot"
fi

API="https://api.github.com/repos/$REPO/releases/tags/$TAG"

# Find matching asset
ASSET_URL=$(curl -fsSL "$API" \
  | grep -o "\"browser_download_url\": \"[^\"]*Installer-${OS}-${ARCH}[^\"]*\"" \
  | head -1 \
  | cut -d'"' -f4)

if [ -z "$ASSET_URL" ]; then
  echo "Could not find installer for ${OS}-${ARCH}"
  exit 1
fi

echo "  ${BLUE}channel:${YELLOW}  $CHANNEL${NC}"
echo "  ${BLUE}source:${YELLOW}   $ASSET_URL${NC}"

if [ "$OS" = "win" ]; then
  curl -fsSL -o metatron-vm-installer.exe "$ASSET_URL"
  echo ""
  echo "Downloaded: metatron-vm-installer.exe"
  echo "Run the installer to complete setup."
else
  TMPDIR=$(mktemp -d)
  trap "rm -rf $TMPDIR" EXIT

  if [ "$OS" = "mac" ]; then
    curl -fsSL "$ASSET_URL" | tar xz -C "$TMPDIR"
  else
    curl -fsSL "$ASSET_URL" | tar xz -C "$TMPDIR"
  fi

  INSTALL_DIR="${HOME}/.local/bin"
  mkdir -p "$INSTALL_DIR"

  # Find the metatron-vm binary in extracted files
  BIN=$(find "$TMPDIR" -name "metatron-vm" -o -name "metatron" | head -1)
  if [ -z "$BIN" ]; then
    echo "Binary not found in archive"
    ls -la "$TMPDIR"
    exit 1
  fi

  cp "$BIN" "$INSTALL_DIR/metatron-vm"
  chmod +x "$INSTALL_DIR/metatron-vm"

  echo ""
  echo -e "${GREEN}metatron-vm installed to ${INSTALL_DIR}/metatron-vm${NC}"
  echo ""
  echo "Add to PATH if needed:"
  echo "  export PATH=\$HOME/.local/bin:\$PATH"
  echo ""
  echo "Usage:"
  echo "  metatron-vm \"[host=><ws://0.0.0.0:8888>]\""
  echo ""
  echo "No arguments start a bare VM (core ISA only):"
  echo "  metatron-vm"
fi
