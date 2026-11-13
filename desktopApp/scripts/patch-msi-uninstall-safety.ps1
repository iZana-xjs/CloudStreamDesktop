param(
    [Parameter(Mandatory = $true)]
    [string] $MsiPath
)

$ErrorActionPreference = 'Stop'
$msiPath = [IO.Path]::GetFullPath($MsiPath)
if (-not (Test-Path -LiteralPath $msiPath -PathType Leaf)) {
    throw "MSI not found: $msiPath"
}

$stagingPath = "$msiPath.safety-staging"
$backupPath = "$msiPath.pre-safety-backup"
Remove-Item -LiteralPath $stagingPath, $backupPath -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath $msiPath -Destination $stagingPath

$installer = $null
$database = $null
try {
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.OpenDatabase($stagingPath, 1) # msiOpenDatabaseModeTransact

    function Get-MsiRows([string] $Sql, [int] $ColumnCount) {
        Write-Verbose "MSI SQL: $Sql"
        $view = $database.OpenView($Sql)
        try {
            $view.Execute()
            $rows = [System.Collections.Generic.List[object]]::new()
            while ($record = $view.Fetch()) {
                $values = @()
                for ($column = 1; $column -le $ColumnCount; $column++) {
                    $values += $record.StringData($column)
                }
                [void]$rows.Add([pscustomobject]@{ Values = $values })
            }
            return $rows.ToArray()
        } finally {
            $view.Close()
        }
    }

    function Invoke-MsiSql([string] $Sql) {
        $view = $database.OpenView($Sql)
        try {
            $view.Execute()
        } finally {
            $view.Close()
        }
    }

    $registryRows = @(Get-MsiRows "SELECT ``Registry``, ``Component_``, ``Name`` FROM ``Registry``" 3 | Where-Object { $_ -and $_.Values[2].StartsWith('RM_RF', [StringComparison]::OrdinalIgnoreCase) })
    $sequenceRows = @(Get-MsiRows "SELECT ``Action`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'WixRemoveFoldersEx'" 1 | Where-Object { $_ -and $_.Values[0] -eq 'WixRemoveFoldersEx' })
    $removeFolderRows = @(Get-MsiRows "SELECT ``Component_`` FROM ``WixRemoveFolderEx``" 1 | Where-Object { $_ -and $_.Values[0].StartsWith('crm_rf', [StringComparison]::OrdinalIgnoreCase) })
    $appSearchRows = @(Get-MsiRows "SELECT ``Property``, ``Signature_`` FROM ``AppSearch``" 2 | Where-Object { $_ -and $_.Values[0].StartsWith('RM_RF', [StringComparison]::OrdinalIgnoreCase) })
    if ($registryRows.Count -ne 1 -or $sequenceRows.Count -ne 1 -or $removeFolderRows.Count -ne 1) {
        throw "Unexpected jpackage cleanup metadata: RM_RF registry rows=$($registryRows.Count), Wix cleanup sequence rows=$($sequenceRows.Count), WixRemoveFolderEx rows=$($removeFolderRows.Count). MSI left untouched."
    }

    $componentIds = @($registryRows | ForEach-Object { $_.Values[1] } | Select-Object -Unique)
    foreach ($searchRow in $appSearchRows) {
        $property = $searchRow.Values[0].Replace("'", "''")
        $signature = $searchRow.Values[1].Replace("'", "''")
        Invoke-MsiSql "DELETE FROM ``AppSearch`` WHERE ``Property`` = '$property'"
        Invoke-MsiSql "DELETE FROM ``RegLocator`` WHERE ``Signature_`` = '$signature'"
    }

    foreach ($componentId in $componentIds) {
        $escapedComponent = $componentId.Replace("'", "''")

        Invoke-MsiSql "DELETE FROM ``FeatureComponents`` WHERE ``Component_`` = '$escapedComponent'"
        Invoke-MsiSql "DELETE FROM ``WixRemoveFolderEx`` WHERE ``Component_`` = '$escapedComponent'"
        foreach ($registryRow in $registryRows | Where-Object { $_.Values[1] -eq $componentId }) {
            $registryId = $registryRow.Values[0].Replace("'", "''")
            Invoke-MsiSql "DELETE FROM ``Registry`` WHERE ``Registry`` = '$registryId'"
        }
        Invoke-MsiSql "DELETE FROM ``Component`` WHERE ``Component`` = '$escapedComponent'"
    }

    Invoke-MsiSql "DELETE FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'WixRemoveFoldersEx'"
    Invoke-MsiSql "DELETE FROM ``InstallUISequence`` WHERE ``Action`` = 'WixRemoveFoldersEx'"
    Invoke-MsiSql "DELETE FROM ``CustomAction`` WHERE ``Action`` = 'WixRemoveFoldersEx'"

    # Verify the generated installer database no longer contains the recursive
    # action, registry seed, or component before replacing the original MSI.
    foreach ($sql in @(
        "SELECT ``Action`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'WixRemoveFoldersEx'",
        "SELECT ``Action`` FROM ``CustomAction`` WHERE ``Action`` = 'WixRemoveFoldersEx'",
        "SELECT ``Component_`` FROM ``WixRemoveFolderEx``"
    )) {
        $view = $database.OpenView($sql)
        try {
            $view.Execute()
            if ($view.Fetch()) { throw "Unsafe MSI cleanup row remains: $sql" }
        } finally {
            $view.Close()
        }
    }
    foreach ($check in @(
        @{ Sql = 'SELECT `Name` FROM `Registry`'; Prefix = 'RM_RF' },
        @{ Sql = 'SELECT `Component` FROM `Component`'; Prefix = 'crm_rf' },
        @{ Sql = 'SELECT `Property` FROM `AppSearch`'; Prefix = 'RM_RF' }
    )) {
        $view = $database.OpenView($check.Sql)
        try {
            $view.Execute()
            while ($record = $view.Fetch()) {
                if ($record.StringData(1).StartsWith($check.Prefix, [StringComparison]::OrdinalIgnoreCase)) {
                    throw "Unsafe MSI cleanup metadata remains in query: $($check.Sql)"
                }
            }
        } finally {
            $view.Close()
        }
    }
    $database.Commit()
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database)
    $database = $null

} catch {
    $failure = $_
    if ($database) {
        try { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) } catch {}
        $database = $null
    }
    if ($installer) {
        try { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) } catch {}
        $installer = $null
    }
    try { if (Test-Path -LiteralPath $stagingPath) { Remove-Item -LiteralPath $stagingPath -Force -ErrorAction Stop } } catch {}
    throw $failure
} finally {
    if ($installer) {
        try { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) } catch {}
    }
}

Write-Output "Patched and verified MSI staging database: $stagingPath"
