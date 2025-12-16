#!/bin/bash
set -e

# Capturing the new version passed by Semantic Release
NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then
  echo "Error: No version argument supplied."
  exit 1
fi

echo "🚀 Bumping Android version to: $NEW_VERSION"

MODULE_FILES=(
  "NewRelicVideoCore/build.gradle"
  "NRExoPlayerTracker/build.gradle"
  "NRIMATracker/build.gradle"
)

# 3. Loop through each file and update the version
for GRADLE_FILE in "${MODULE_FILES[@]}"; do

  if [ -f "$GRADLE_FILE" ]; then
      echo "   👉 Updating $GRADLE_FILE..."

      sed -i.bak "s/versionName \".*\"/versionName \"$NEW_VERSION\"/" "$GRADLE_FILE"

      # Clean up backup file created by sed
      rm "${GRADLE_FILE}.bak"
  else
      echo "   ⚠️ Warning: Could not find file $GRADLE_FILE. Skipping."
  fi

done

echo "✅ All modules updated to version $NEW_VERSION"