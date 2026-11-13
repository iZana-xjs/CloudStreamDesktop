param(
    [Parameter(Mandatory = $true)]
    [string] $MsiPath
)

$ErrorActionPreference = 'Stop'
$msiPath = [IO.Path]::GetFullPath($MsiPath)
$stagingPath = "$msiPath.safety-staging"
$backupPath = "$msiPath.pre-safety-backup"
if (-not (Test-Path -LiteralPath $stagingPath -PathType Leaf)) {
    throw "Safety-patched MSI staging file is missing: $stagingPath"
}

Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
Move-Item -LiteralPath $msiPath -Destination $backupPath
try {
    Move-Item -LiteralPath $stagingPath -Destination $msiPath
    Remove-Item -LiteralPath $backupPath -Force
} catch {
    if ((Test-Path -LiteralPath $backupPath) -and -not (Test-Path -LiteralPath $msiPath)) {
        Move-Item -LiteralPath $backupPath -Destination $msiPath
    }
    throw
}

Write-Output "Promoted safety-patched MSI: $msiPath"
