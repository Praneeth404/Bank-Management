#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  Bank Management System — Build & Run Script (with MySQL)
#
#  Prerequisites:
#    1. JDK 17+  (needs javac, not just java)
#    2. MySQL running with bank_db created:
#         mysql -u root -p < schema.sql
#    3. lib/mysql-connector-j-9.7.0.jar present
#
#  Usage:
#    chmod +x run.sh
#    ./run.sh          # compile + run
#    ./run.sh build    # compile only
# ─────────────────────────────────────────────────────────────

SRC_DIR="src"
OUT_DIR="out"
LIB_DIR="lib"
JAR="mysql-connector-j-9.7.0.jar"
MAIN_CLASS="Main"

# ── Set terminal to UTF-8 (needed on Windows/Git Bash for ₹ symbol) ──────────
if command -v chcp.com &>/dev/null; then
  chcp.com 65001 > /dev/null
fi

# ── Locate javac ─────────────────────────────────────────────
if ! command -v javac &>/dev/null; then
  echo "    javac not found. Please install a JDK:"
  echo "    sudo apt install default-jdk    # Debian/Ubuntu"
  echo "    sudo dnf install java-21-openjdk-devel  # Fedora/RHEL"
  echo "    brew install openjdk@21          # macOS"
  exit 1
fi

# ── Check connector JAR ───────────────────────────────────────
if [ ! -f "$LIB_DIR/$JAR" ]; then
  echo "    MySQL connector not found at $LIB_DIR/$JAR"
  echo "    Download from: https://dev.mysql.com/downloads/connector/j/"
  exit 1
fi

echo "  Using $(javac -version 2>&1)"
echo "  Connector: $LIB_DIR/$JAR"

# ── Compile ───────────────────────────────────────────────────
mkdir -p "$OUT_DIR"
echo "  Compiling..."

javac -cp "$LIB_DIR/$JAR" -d "$OUT_DIR" "$SRC_DIR"/*.java

if [ $? -ne 0 ]; then
  echo "  Compilation failed."
  exit 1
fi
echo "  Compilation successful."

# ── Run ───────────────────────────────────────────────────────
if [ "$1" != "build" ]; then
  echo "  Starting Bank Management System..."
  echo "─────────────────────────────────────────"

  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT_DIR;$LIB_DIR/$JAR" "$MAIN_CLASS"
fi