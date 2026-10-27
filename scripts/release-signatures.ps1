param([Parameter(Mandatory=$true)][string]$Directory,[string]$Installer)
$ErrorActionPreference = 'Stop'
# PowerShell 7 can pass an incompatible PSModulePath to Windows PowerShell.
Import-Module (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/Modules/Microsoft.PowerShell.Security/Microsoft.PowerShell.Security.psd1') -Force
$targets = @((Join-Path $Directory 'East of Town.exe'),(Join-Path $Directory 'resources/native/ReceiptShare.exe'))
if ($Installer -and (Test-Path -LiteralPath $Installer)) { $targets += $Installer }
$results = foreach ($target in $targets) {
    $signature = Get-AuthenticodeSignature -LiteralPath $target
    [pscustomobject]@{File=$target;Status=$signature.Status.ToString()}
}
ConvertTo-Json -InputObject @($results) -Compress
