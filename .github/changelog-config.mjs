// Applies the release-notes-generator options from .releaserc.json to conventional-changelog-cli,
// which doesn't read .releaserc.json itself. Keeps CHANGELOG.md in the flat `- **Section: text**`
// format that release-notes-docs-pr.yml parses.
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const [, { presetConfig, writerOpts }] = require('../.releaserc.json').plugins.find(
  (p) => Array.isArray(p) && p[0] === '@semantic-release/release-notes-generator'
);

export default {
  options: { preset: { name: 'conventionalcommits', ...presetConfig } },
  writerOpts,
};
