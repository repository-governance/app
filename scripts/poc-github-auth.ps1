$ErrorActionPreference = 'Stop'

# Proof of concept only: secret values stay in process memory and are never emitted.
$stage = 'Git credential retrieval'

function Invoke-GitHubGet([string]$Uri, [string]$BearerToken) {
    if (-not $Uri.StartsWith('https://api.github.com/', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The POC only permits GitHub API HTTPS requests.'
    }
    $headers = @{
        Authorization = "Bearer $BearerToken"
        Accept = 'application/vnd.github+json'
        'X-GitHub-Api-Version' = '2022-11-28'
    }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $Uri `
            -Headers $headers -TimeoutSec 30 -ErrorAction Stop
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = [string]$response.Content
        }
    } catch {
        $httpResponse = $_.Exception.Response
        if ($null -ne $httpResponse) {
            return [pscustomobject]@{
                StatusCode = [int]$httpResponse.StatusCode
                Content = ''
            }
        }
        throw
    } finally {
        $headers.Clear()
    }
}

$git = (Get-Command git.exe -ErrorAction Stop).Source
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = $git
$start.Arguments = 'credential fill'
$start.UseShellExecute = $false
$start.CreateNoWindow = $false
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$start.StandardErrorEncoding = [System.Text.Encoding]::UTF8

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $start
$credentialText = $null
$stderrTask = $null
$stdoutTask = $null
$token = $null

try {
    if (-not $process.Start()) {
        throw 'Could not start Git credential retrieval.'
    }

    # Drain both streams concurrently; do not write either stream to console/log.
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write("protocol=https`nhost=github.com`n`n")
    $process.StandardInput.Close()

    if (-not $process.WaitForExit(120000)) {
        try { $process.Kill() } catch { }
        Write-Output 'CREDENTIAL_ERROR: Git Credential Manager did not finish within 120 seconds.'
        exit 2
    }
    $process.WaitForExit()
    $credentialText = $stdoutTask.GetAwaiter().GetResult()
    $null = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) {
        Write-Output 'CREDENTIAL_ERROR: Git Credential Manager returned no credential. It may need interactive sign-in.'
        exit 2
    }

    $fields = @{}
    foreach ($line in ($credentialText -split "`r?`n")) {
        $separator = $line.IndexOf('=')
        if ($separator -gt 0) {
            $fields[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
        }
    }
    $credentialText = $null
    if (-not $fields.ContainsKey('password') -or [string]::IsNullOrWhiteSpace($fields['password'])) {
        $fields.Clear()
        Write-Output 'CREDENTIAL_ERROR: Git helper did not provide a password/token usable for this REST check.'
        exit 3
    }

    $token = [string]$fields['password']
    $fields.Clear()

    $stage = 'GitHub identity request'
    $authResponse = Invoke-GitHubGet 'https://api.github.com/user' $token
    $authCode = $authResponse.StatusCode
    Write-Output "HTTP $authCode"

    if ($authCode -ne 200) {
        if ($authCode -eq 401) {
            Write-Output 'AUTHENTICATION: Git credential was not accepted as a GitHub REST bearer token; no fallback was attempted.'
        } else {
            Write-Output "AUTHENTICATION: GitHub returned HTTP $authCode."
        }
        exit 4
    }

    $user = $authResponse.Content | ConvertFrom-Json
    $login = [string]$user.login
    $user = $null
    if ([string]::IsNullOrWhiteSpace($login)) {
        Write-Output 'AUTHENTICATION: GitHub returned HTTP 200 without a login.'
        exit 5
    }
    Write-Output "LOGIN $login"

    $organization = 'repository-governance'
    $topic = 'archimate'
    $stage = 'GitHub repository search'
    $query = [Uri]::EscapeDataString("org:$organization topic:$topic")
    $repositories = New-Object System.Collections.Generic.List[string]
    $expectedCount = $null
    $page = 1
    do {
        $uri = "https://api.github.com/search/repositories?q=$query&per_page=100&page=$page"
        $searchResponse = Invoke-GitHubGet $uri $token
        $searchCode = $searchResponse.StatusCode
        if ($searchCode -ne 200) {
            Write-Output "REPOSITORY_SEARCH_HTTP $searchCode"
            exit 6
        }

        $pageResult = $searchResponse.Content | ConvertFrom-Json
        if ($pageResult.incomplete_results) {
            Write-Output 'REPOSITORY_SEARCH: GitHub marked the search incomplete; partial names were discarded.'
            exit 7
        }
        if ($null -eq $expectedCount) {
            $expectedCount = [long]$pageResult.total_count
            if ($expectedCount -gt 1000) {
                Write-Output 'REPOSITORY_SEARCH: More than 1,000 matches; narrow the search. Partial names were discarded.'
                exit 8
            }
        } elseif ([long]$pageResult.total_count -ne $expectedCount) {
            Write-Output 'REPOSITORY_SEARCH: Result count changed while paging; partial names were discarded.'
            exit 9
        }

        foreach ($repository in $pageResult.items) {
            $repositories.Add([string]$repository.name)
        }
        $pageResult = $null
        $page++
    } while ($repositories.Count -lt $expectedCount -and $page -le 10)

    if ($repositories.Count -ne $expectedCount) {
        Write-Output 'REPOSITORY_SEARCH: Incomplete page set; partial names were discarded.'
        exit 10
    }

    if ($repositories.Count -eq 0) {
        Write-Output 'REPOSITORIES (none)'
    } else {
        foreach ($repositoryName in $repositories) {
            Write-Output $repositoryName
        }
    }
}
catch {
    # Keep diagnostics to an exception category only; omit messages, stdout, and headers.
    $failureType = $_.Exception.GetType().FullName
    Write-Output "POC_ERROR at $stage ($failureType; details suppressed)."
    exit 11
}
finally {
    $token = $null
    $credentialText = $null
    if ($null -ne $process) { $process.Dispose() }
}
