#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec mise exec -- ktlint --relative "$@" 'modules/**/*.kt' '!modules/*/android/build/**'
