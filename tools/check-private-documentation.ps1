param([string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent))

$ErrorActionPreference = 'Stop'
$tracked = @(& git -C $RepositoryRoot ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect tracked files.' }
$allowedMarkdown = @('README.md', 'UPSTREAM.md')
$privateFiles = @($tracked | Where-Object {
    $_ -like 'docs/*' -or $_ -eq 'AGENTS.md' -or
    ($_ -like '*.md' -and $_ -notin $allowedMarkdown -and $_ -notlike 'LICENSES/*')
})
if ($privateFiles.Count) {
    $privateFiles | ForEach-Object { Write-Host "Internal documentation must not be tracked: $_" }
    throw 'Private documentation was included in the public source tree.'
}
Write-Host 'Public source tree contains no internal documentation.'
