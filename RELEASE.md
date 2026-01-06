# Release Process Guide

This document explains the automated release workflow for the New Relic Video Agent Android SDK.

---

## Table of Contents

- [Overview](#overview)
- [Conventional Commits](#conventional-commits)
- [Version Bumping](#version-bumping)
- [Release Workflow](#release-workflow)
- [Git Hook Validation](#git-hook-validation)
- [Troubleshooting](#troubleshooting)

---

## Overview

This project uses **automated semantic versioning** based on conventional commits. The release process is split into two phases:

| Phase | Workflow | Trigger | Action |
|-------|----------|---------|--------|
| **Prepare** | `release.yml` | Push to `master` | Creates release PR with version bump + changelog |
| **Publish** | `publish.yml` | Merge release PR | Creates Git tag + GitHub Release |

---

## Conventional Commits

All commit messages **must** follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>)?: <subject>

<body>

<footer>
```

### Commit Types

| Type | Description | Version Bump | Changelog Section |
|------|-------------|--------------|-------------------|
| `feat:` | New feature | **MINOR** | Features |
| `fix:` | Bug fix | **PATCH** | Bug Fixes |
| `perf:` | Performance improvement | **PATCH** | Performance Improvements |
| `revert:` | Revert changes | **PATCH** | Reverts |
| `build:` | Build system changes | **PATCH** | Build System |
| `docs:` | Documentation only | No release | Hidden |
| `style:` | Formatting, no code change | No release | Hidden |
| `refactor:` | Code restructuring | No release | Hidden |
| `test:` | Adding tests | No release | Hidden |
| `ci:` | CI/CD changes | No release | Hidden |
| `chore:` | Maintenance tasks | No release | Hidden |

### Breaking Changes (MAJOR bump)

```bash
# Option 1: Add ! after type
feat!: redesign tracking API
fix!: change method signature

# Option 2: Add BREAKING CHANGE in body
feat: new authentication system

BREAKING CHANGE: removed legacy login method
```

### Examples

```bash
# Simple fix
fix: resolve null pointer exception in video player

# Feature with scope
feat(tracker): add custom event tracking

# Breaking change
feat!: redesign API interface

BREAKING CHANGE: removed deprecated methods

# Documentation (no release)
docs: update README with new examples

# Multiple line body
fix(player): handle edge case in seek functionality

Fixed issue where player crashed when seeking
beyond video duration. Added boundary checks.

Closes #123
```

---

## Version Bumping

Version is calculated automatically based on commits since the last release.

### Priority Order

```
MAJOR > MINOR > PATCH
```

### Examples

| Commits | Highest Type | Version Change |
|---------|--------------|----------------|
| 3 `fix:` + 2 `feat:` | `feat:` | `4.0.4` → `4.1.0` |
| 5 `fix:` + 1 `feat!:` | `feat!:` (breaking) | `4.0.4` → `5.0.0` |
| 10 `fix:` only | `fix:` | `4.0.4` → `4.0.5` |
| `chore:` + `docs:` only | none | No release |

### Version Storage

Version is stored in `gradle.properties`:

```properties
GLOBAL_VERSION_NAME=4.0.5
```

---

## Release Workflow

### Step 1: Developer Creates PR

```
feature-branch → master
     ↓
Commits: fix: bug, feat: feature, etc.
     ↓
PR merged to master
```

### Step 2: Release Preparation (release.yml)

```
Push to master detected
         ↓
┌─────────────────────────────────────┐
│  Analyze commits with semantic-     │
│  release to determine next version  │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  Create release branch:             │
│  release/4.1.0                      │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  Update files:                      │
│  - gradle.properties (version)      │
│  - CHANGELOG.md (release notes)     │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  Create PR with "release" label:    │
│  "chore(release): 4.1.0"            │
└─────────────────────────────────────┘
```

### Step 3: Review & Merge Release PR

- Review the generated changelog
- Verify version bump is correct
- Merge the release PR

### Step 4: Publish Release (publish.yml)

```
Release PR merged
         ↓
┌─────────────────────────────────────┐
│  Extract version from               │
│  gradle.properties                  │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  Extract release notes from         │
│  CHANGELOG.md                       │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  Create:                            │
│  - Git tag: v4.1.0                  │
│  - GitHub Release with notes        │
└─────────────────────────────────────┘
```

---

## Git Hook Validation

A commit-msg hook is installed automatically to validate commit messages.

### Installation

The hook is installed automatically when you build the project:

```bash
./gradlew build
```

Or install manually:

```bash
cp scripts/commit-msg .git/hooks/
chmod +x .git/hooks/commit-msg
```

### Validation

```bash
# Wrong format - REJECTED
$ git commit -m "fixed bug"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ERROR: Invalid commit message format!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Your message:
  fixed bug

Valid format: <type>(<scope>)?: <subject>

Allowed types:
  feat:      New feature (MINOR version bump)
  fix:       Bug fix (PATCH version bump)
  ...

# Correct format - ACCEPTED
$ git commit -m "fix: resolve bug"
[main abc1234] fix: resolve bug
```

---

## Troubleshooting

### No Release Created

**Cause:** All commits use types that don't trigger releases (`chore:`, `docs:`, etc.)

**Solution:** Ensure at least one commit uses `feat:`, `fix:`, `perf:`, `revert:`, or `build:`

---

### Wrong Version Bump

**Cause:** Commit message format incorrect

**Solution:** Verify commit format:
- `feat: add feature` (lowercase, colon, space)
- `Feat: add feature` (uppercase - WRONG)
- `feat add feature` (missing colon - WRONG)

---

### Breaking Change Not Detected

**Cause:** Using wrong preset in `.releaserc.json`

**Solution:** Verify `.releaserc.json` has:
```json
"preset": "conventionalcommits"
```

---

### Commit Rejected by Hook

**Cause:** Commit message doesn't follow conventional format

**Solution:** Use correct format:
```bash
# Wrong
git commit -m "fixed the bug"

# Correct
git commit -m "fix: resolve the bug"
```

---

### zsh: illegal modifier

**Cause:** Using `!` in double quotes in zsh

**Solution:** Use single quotes:
```bash
# Wrong
git commit -m "feat!: breaking change"

# Correct
git commit -m 'feat!: breaking change'
```

---

## Configuration Files

| File | Purpose |
|------|---------|
| `.github/workflows/release.yml` | Creates release PR with version bump |
| `.github/workflows/publish.yml` | Creates Git tag and GitHub Release |
| `.releaserc.json` | Semantic release configuration |
| `scripts/commit-msg` | Git hook for commit validation |
| `scripts/bump_version.sh` | Script to update version in gradle |
| `gradle.properties` | Stores current version |
| `CHANGELOG.md` | Auto-generated release notes |

---

## Quick Reference

```bash
# Feature (MINOR bump)
git commit -m "feat: add new tracking method"

# Bug fix (PATCH bump)
git commit -m "fix: resolve crash on startup"

# Breaking change (MAJOR bump)
git commit -m 'feat!: redesign API'

# No release
git commit -m "docs: update README"
git commit -m "chore: cleanup code"
```



