const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const packageDir = path.join(root, 'release', 'store-test');
const packagePath = path.join(packageDir, `East of Town Store Test-${require('../package.json').version}-x64.appx`);
const extracted = path.join(packageDir, 'inspection');
const archiveCopy = path.join(packageDir, 'package-inspection.zip');
assert.ok(fs.statSync(packagePath).size > 0, `Missing Store package: ${packagePath}`);
fs.rmSync(extracted, { recursive: true, force: true });
fs.mkdirSync(extracted, { recursive: true });
fs.copyFileSync(packagePath, archiveCopy);

const expanded = spawnSync('powershell.exe', [
  '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(__dirname, 'expand-store-package.ps1'),
  '-ArchivePath', archiveCopy, '-DestinationPath', extracted,
], { encoding: 'utf8', windowsHide: true });
fs.rmSync(archiveCopy, { force: true });
assert.equal(expanded.status, 0, expanded.stderr || expanded.stdout || 'Could not inspect the Store package archive.');

const manifestPath = path.join(extracted, 'AppxManifest.xml');
const manifest = fs.readFileSync(manifestPath, 'utf8');
assert.match(manifest, /Name="EastOfTownPOS\.StoreTest"/, 'Test package identity is missing.');
assert.match(manifest, /Publisher="CN=ms"/, 'Test package publisher should be the explicit Store-test placeholder.');
assert.match(manifest, /runFullTrust/, 'Electron package must declare runFullTrust.');
for (const relative of [
  'app/East of Town.exe',
  'app/resources/app.asar',
  'app/resources/backend/pos.jar',
  'app/resources/runtime/bin/java.exe',
  'app/resources/native/ReceiptShare.exe',
]) {
  assert.ok(fs.statSync(path.join(extracted, relative)).size > 0, `Missing packaged runtime file: ${relative}`);
}
assert.equal(fs.readdirSync(extracted, { recursive: true }).some(name => /\.db(?:-wal|-shm)?$/i.test(String(name))), false, 'A database must never be shipped inside the package.');
console.log(`Store package contents passed inspection: ${packagePath}`);
console.log('Bundled Electron app, Spring Boot JAR, Java runtime and Windows receipt helper are present; no database is packaged.');
console.log('This is an unsigned test identity package. Install only after Store signing or approved test signing; it is not an upload-ready Partner Center package.');
