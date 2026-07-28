#!/bin/bash

# Script to check current beta version generation
# Usage: ./scripts/check_beta_version.sh

set -e

echo "🔍 Checking Beta Version Generation"
echo "=================================="

# Get current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "Current branch: $CURRENT_BRANCH"

# Get base version
BASE_VERSION=$(grep "^GLOBAL_VERSION_NAME=" gradle.properties | cut -d'=' -f2)
echo "Base version: $BASE_VERSION"

# Show what version would be generated
echo ""
echo "📦 Generated Versions:"
if [ "$CURRENT_BRANCH" = "stable-beta" ]; then
    TIMESTAMP=$(date +"%Y%m%d%H%M")
    BETA_VERSION="${BASE_VERSION}-beta.${TIMESTAMP}"
    echo "  Beta version: $BETA_VERSION"
    echo "  JitPack usage: com.github.newrelic.[module]:${BETA_VERSION}"
else
    echo "  Regular version: $BASE_VERSION"
    echo "  JitPack usage: com.github.newrelic.[module]:${BASE_VERSION}"
fi

echo ""
echo "✅ Version check complete!"

if [ "$CURRENT_BRANCH" = "stable-beta" ]; then
    echo ""
    echo "💡 Tips for beta releases:"
    echo "  - Merge PR to stable-beta to trigger beta release"
    echo "  - Beta versions include timestamp for uniqueness"
    echo "  - Beta releases are marked as 'pre-release' on GitHub"
fi