$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$framework = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319'
$metadata = Join-Path $env:WINDIR 'System32/WinMetadata'
$facade = Get-ChildItem -LiteralPath (Join-Path $env:WINDIR 'Microsoft.NET/assembly/GAC_MSIL/System.Runtime') -Recurse -Filter System.Runtime.dll | Select-Object -First 1
if (-not $facade) { throw '.NET Framework 4.8 Windows Runtime references are required.' }
$output = Join-Path $project 'native/bin'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$references = @('System.dll','System.Core.dll','System.Windows.Forms.dll', $facade.FullName, (Join-Path $framework 'System.Runtime.WindowsRuntime.dll'), (Join-Path $framework 'System.Runtime.InteropServices.WindowsRuntime.dll'))
foreach ($name in @('Windows.ApplicationModel','Windows.Foundation','Windows.Storage')) { $references += Join-Path $metadata ($name + '.winmd') }
$compilerArgs = @('/nologo','/target:exe','/platform:x64',('/out:' + (Join-Path $output 'ReceiptShare.exe')))
foreach ($reference in $references) { $compilerArgs += '/reference:' + $reference }
$compilerArgs += Join-Path $project 'native/ReceiptShare.cs'
& (Join-Path $framework 'csc.exe') @compilerArgs
if ($LASTEXITCODE -ne 0) { throw 'Receipt sharing helper compilation failed.' }
if ($env:EOT_SIGN_CERT_THUMBPRINT) {
    Import-Module (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/Modules/Microsoft.PowerShell.Security/Microsoft.PowerShell.Security.psd1') -Force
    if ($env:EOT_SIGN_CERT_THUMBPRINT -notmatch '^[A-Fa-f0-9]{40}$') { throw 'Invalid signing certificate thumbprint.' }
    $certificate = Get-Item -LiteralPath ('Cert:/CurrentUser/My/' + $env:EOT_SIGN_CERT_THUMBPRINT)
    if (-not $certificate.HasPrivateKey) { throw 'Signing certificate has no private key.' }
    $signature = Set-AuthenticodeSignature -LiteralPath (Join-Path $output 'ReceiptShare.exe') -Certificate $certificate -HashAlgorithm SHA256 -TimestampServer 'http://timestamp.digicert.com'
    if ($signature.Status -ne 'Valid') { throw 'Native helper signing did not produce a valid trusted signature.' }
}
Write-Output 'Windows receipt sharing helper compiled.'
