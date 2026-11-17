/*
 * Writes SHA256SUMS and integrity-manifest.json for the given release files.
 * Uses only node built-ins.
 *
 * Usage: node generate-integrity-manifest.mjs <out-dir> <version> <commit> <file>...
 *
 * Outputs (in <out-dir>):
 *   SHA256SUMS              sha256sum format, checkable with `sha256sum -c` / `shasum -a 256 -c`
 *   integrity-manifest.json { version, commit, files: { <name>: { sha256, sha384 } } }
 *                           sha256 is hex, sha384 is an SRI value ("sha384-<base64>")
 */
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { basename, join } from 'node:path'

const [outDir, version, commit, ...inputs] = process.argv.slice(2)
const GENERATED = new Set(['SHA256SUMS', 'integrity-manifest.json'])
const files = inputs.filter((f) => !GENERATED.has(basename(f)))

if (!outDir || !version || !commit || files.length === 0) {
  console.error('Usage: generate-integrity-manifest.mjs <out-dir> <version> <commit> <file>...')
  process.exit(1)
}

const names = files.map((f) => basename(f))
if (new Set(names).size !== names.length) {
  console.error('Duplicate file names in the release file list')
  process.exit(1)
}

const manifest = { version, commit, files: {} }
const sums = []
for (const file of files.sort((a, b) => basename(a).localeCompare(basename(b)))) {
  const contents = readFileSync(file)
  const name = basename(file)
  const sha256 = createHash('sha256').update(contents).digest('hex')
  const sha384 = `sha384-${createHash('sha384').update(contents).digest('base64')}`
  manifest.files[name] = { sha256, sha384 }
  sums.push(`${sha256}  ${name}`)
}

writeFileSync(join(outDir, 'SHA256SUMS'), sums.join('\n') + '\n')
writeFileSync(join(outDir, 'integrity-manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
console.log(`Wrote SHA256SUMS and integrity-manifest.json for v${version} (${sums.length} files)`)
