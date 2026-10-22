$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$target = Join-Path $projectRoot 'runtime'
if (Test-Path -LiteralPath $target) { throw 'runtime already exists; keep the verified runtime or choose a new build workspace.' }
$assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?os=windows&architecture=x64&image_type=jre&vendor=eclipse'
$package = $assets[0].binary.package
$staging = Join-Path $projectRoot ('runtime-staging-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
$archive = Join-Path $staging 'temurin.zip'
Invoke-WebRequest -Uri $package.link -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum.ToLowerInvariant()) { throw 'Java runtime checksum mismatch; archive was not extracted.' }
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $staging 'extracted')
$runtimeSource = Get-ChildItem -LiteralPath (Join-Path $staging 'extracted') -Directory | Select-Object -First 1
if (-not (Test-Path -LiteralPath (Join-Path $runtimeSource.FullName 'bin/java.exe'))) { throw 'Invalid runtime structure' }
Copy-Item -LiteralPath $runtimeSource.FullName -Destination $target -Recurse
$assets[0] | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $projectRoot 'docs/runtime-manifest.json')
Write-Output "Verified runtime copied to $target. Staging archive retained at $archive."
