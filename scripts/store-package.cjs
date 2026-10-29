const path = require('node:path');
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const testPackage = process.argv.includes('--test');

const identityName = testPackage ? 'EastOfTownPOS.StoreTest' : process.env.EOT_STORE_IDENTITY;
const publisher = testPackage ? 'CN=ms' : process.env.EOT_STORE_PUBLISHER;
const publisherDisplayName = testPackage ? 'East of Town POS Test' : process.env.EOT_STORE_PUBLISHER_DISPLAY_NAME;

if (!testPackage && (!identityName || !publisher || !publisherDisplayName)) {
  console.error('Set EOT_STORE_IDENTITY, EOT_STORE_PUBLISHER and EOT_STORE_PUBLISHER_DISPLAY_NAME from the app identity in Partner Center.');
  process.exit(2);
}

const packageJson = require('../package.json');
const output = path.join(root, 'release', testPackage ? 'store-test' : 'store');
fs.mkdirSync(output, { recursive: true });
const configPath = path.join(output, 'store-builder-config.json');
const config = {
  directories: { output },
  win: { target: [{ target: 'appx', arch: ['x64'] }] },
  appx: {
    identityName,
    applicationId: 'EastOfTownPOS',
    publisher,
    publisherDisplayName,
    displayName: 'East of Town POS',
    languages: ['en-US'],
    capabilities: ['runFullTrust'],
    minVersion: '10.0.19041.0',
    artifactName: testPackage ? 'East of Town Store Test-${version}-${arch}.${ext}' : 'East of Town Store-${version}-${arch}.${ext}',
  },
};
fs.writeFileSync(configPath, JSON.stringify(config, null, 2));

const cli = path.join(root, 'node_modules', 'electron-builder', 'cli.js');
const result = spawnSync(process.execPath, [cli, '--win', 'appx', '--x64', '--config', configPath], {
  cwd: root,
  stdio: 'inherit',
  windowsHide: true,
});
if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status || 1);
