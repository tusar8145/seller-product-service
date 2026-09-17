#!/usr/bin/env bash
# =====================================================================
# run.sh  - Build and run Seller Product Service
#
# Usage:
#   ./run.sh               # dev mode: spring-boot:run (DevTools hot reload ON)
#   ./run.sh --jar         # packaged mode: build jar and run (no hot reload)
#   ./run.sh --skip-build  # skip the pre-build step (dev mode only)
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

MODE="dev"
SKIP_BUILD="false"

for arg in "$@"; do
    case "$arg" in
        --jar)        MODE="jar" ;;
        --skip-build) SKIP_BUILD="true" ;;
        -h|--help)
            grep '^#' "$0" | sed 's/^# \{0,1\}//' | head -n 12
            exit 0
            ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

# ---------- 1. Load env ----------
if [ ! -f ".env.dev" ]; then
    echo "❌ .env.dev not found!"
    exit 1
fi

echo "🔧 Loading environment from .env.dev"
set -o allexport
# shellcheck disable=SC1091
source .env.dev
set +o allexport

# ---------- 2. Sanity check ----------
echo "ℹ️  DB_HOST=${DB_HOST:-<unset>}  KAFKA_URI=${KAFKA_URI:-<unset>}  REDIS_HOST=${REDIS_HOST:-<unset>}"
java -version 2>&1 | head -n 1

# ---------- 3. Maven wrapper detection ----------
if [ -x "./mvnw" ]; then
    MVN="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
else
    echo "❌ Neither ./mvnw nor system 'mvn' is available."
    echo "   Install Maven, or generate the wrapper with: mvn -N wrapper:wrapper"
    exit 1
fi

PORT="${SERVER_PORT:-8081}"

# =====================================================================
# DEV MODE  →  mvn spring-boot:run  (DevTools hot reload enabled)
# =====================================================================
if [ "$MODE" = "dev" ]; then
    if [ "$SKIP_BUILD" != "true" ]; then
        echo "📦 Compiling (skip tests)..."
        "$MVN" -q -DskipTests compile
    fi

    echo "🚀 Starting (dev mode) on port $PORT — DevTools hot reload enabled"
    echo "   Edit Java files in your IDE (with auto-compile ON) to trigger a restart."
    exec "$MVN" spring-boot:run \
        "-Dspring-boot.run.profiles=dev" \
        "-Dspring-boot.run.arguments=--server.port=$PORT"
fi

# =====================================================================
# JAR MODE  →  build once, run frozen jar  (no hot reload)
# =====================================================================
echo "📦 Building project (skip tests)..."
"$MVN" -q -DskipTests clean package

JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -n1 || true)
if [ -z "${JAR:-}" ]; then
    echo "❌ No runnable jar found in ./target"
    exit 1
fi

echo "🚀 Starting: $JAR on port $PORT"
exec java -jar "$JAR" \
    --spring.profiles.active=dev \
    --server.port="$PORT"