#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
java -version
mvn -version
mvn clean test
