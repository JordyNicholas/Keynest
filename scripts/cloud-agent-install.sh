#!/usr/bin/env bash
# Cloud Agent environment bootstrap for Keynest.
#
# Idempotent setup that prepares a checked-out Keynest workspace for building,
# testing, and running both offerings (the Java tools + Manager API, and the
# Angular Manager web app). Safe to re-run: existing tools/caches are reused.
#
#   1. Install Apache Maven if it is missing (the base image ships Java 21 and
#      Node, but not Maven).
#   2. Build + install all Maven modules into the local repo so the Manager API
#      can resolve its sibling library jars when run standalone
#      (e.g. `mvn -pl apps/manager-api spring-boot:run`).
#   3. Install the Angular Manager web dependencies.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

MAVEN_VERSION="3.9.16"
MAVEN_HOME="/opt/apache-maven-${MAVEN_VERSION}"
MAVEN_TARBALL="apache-maven-${MAVEN_VERSION}-bin.tar.gz"

install_maven() {
  if command -v mvn >/dev/null 2>&1 && mvn -version 2>/dev/null | grep -q "Apache Maven ${MAVEN_VERSION}"; then
    echo "Apache Maven ${MAVEN_VERSION} already installed."
    return
  fi

  echo "Installing Apache Maven ${MAVEN_VERSION}..."
  local tmp
  tmp="$(mktemp -d)"

  # Current releases live on dlcdn; older point releases fall off to archive.
  if ! curl -fsSL "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/${MAVEN_TARBALL}" -o "$tmp/maven.tar.gz"; then
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/${MAVEN_TARBALL}" -o "$tmp/maven.tar.gz"
  fi

  sudo rm -rf "$MAVEN_HOME"
  sudo tar -xzf "$tmp/maven.tar.gz" -C /opt
  sudo ln -sfn "$MAVEN_HOME/bin/mvn" /usr/local/bin/mvn
  sudo ln -sfn "$MAVEN_HOME/bin/mvnDebug" /usr/local/bin/mvnDebug
  rm -rf "$tmp"
}

install_maven
mvn -version

echo "Building and installing Maven modules (skipping tests)..."
mvn -B -DskipTests install

echo "Installing Angular Manager web dependencies..."
npm --prefix apps/manager-web ci

echo "Cloud Agent environment bootstrap complete."
