#!/bin/bash

# Get the new version passed from semantic-release
NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then
  echo "Error: No version argument provided."
  exit 1
fi

echo "Bumping version to $NEW_VERSION in gradle.properties..."

# Use sed to find the line starting with GLOBAL_VERSION_NAME and replace it
# This syntax works on both Linux (GitHub Actions) and Mac (Locally)
sed -i.bak "s/^GLOBAL_VERSION_NAME=.*/GLOBAL_VERSION_NAME=$NEW_VERSION/" gradle.properties

# Remove the backup file created by sed
rm -f gradle.properties.bak

echo "Successfully updated gradle.properties to $NEW_VERSION"