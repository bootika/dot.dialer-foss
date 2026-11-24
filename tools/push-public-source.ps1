#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path $PSScriptRoot -Parent),
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$gitRoot = $root.Replace('\', '/')

function Invoke-ReleaseGit {
    & git -c "safe.directory=$gitRoot" -C $root @args
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($args[0])." }
}

$origin = Invoke-ReleaseGit remote get-url --push origin
if ($origin -cnotin @('https://github.com/bootika/dot.dialer-foss.git', 'git@github.com:bootika/dot.dialer-foss.git')) {
    throw 'Use the public Dot Dialer checkout and its established origin.'
}
if ((Invoke-ReleaseGit branch --show-current) -cne 'main') {
    throw 'Use the isolated public main checkout.'
}
if (Invoke-ReleaseGit status --porcelain) {
    throw 'Commit the reviewed public snapshot before testing and publishing it.'
}

$commit = Invoke-ReleaseGit rev-parse HEAD
$stage = [IO.Directory]::CreateTempSubdirectory('dot-dialer-public-')
try {
    $archive = Join-Path $stage.FullName 'source.tar'
    $snapshot = (New-Item -ItemType Directory -Path (Join-Path $stage.FullName 'source')).FullName
    Invoke-ReleaseGit archive --format=tar "--output=$archive" $commit
    & tar -xf $archive -C $snapshot
    if ($LASTEXITCODE -ne 0) { throw 'Could not extract the exact committed source.' }

    $gradle = if ($IsWindows) { '.\gradlew.bat' } else { './gradlew' }
    $lintArgs = @('--no-daemon', '--max-workers=1', '-Pkotlin.compiler.execution.strategy=in-process', ':app:lintFossDebug')
    if ($Offline) { $lintArgs += '--offline' }
    Push-Location -LiteralPath $snapshot
    try {
        & $gradle @lintArgs
        if ($LASTEXITCODE -ne 0) { throw "FOSS lint failed for $commit. Push stopped." }
    } finally {
        Pop-Location
    }

    if ((Invoke-ReleaseGit rev-parse HEAD) -cne $commit -or (Invoke-ReleaseGit status --porcelain)) {
        throw 'The checkout changed after the preflight. Push stopped.'
    }
    Invoke-ReleaseGit push origin "${commit}:refs/heads/main"
    $remoteRef = Invoke-ReleaseGit ls-remote --exit-code origin refs/heads/main
    if (($remoteRef -split '\s+')[0] -cne $commit) {
        throw "Could not confirm $commit as the remote main tip."
    }
    Write-Output "Verified public commit: $commit"
} finally {
    $resolvedStage = (Resolve-Path -LiteralPath $stage.FullName).Path
    if ($resolvedStage -ceq $stage.FullName -and $stage.Name.StartsWith('dot-dialer-public-')) {
        [IO.Directory]::Delete($resolvedStage, $true)
    }
}
