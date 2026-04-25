param(
    [Parameter(Mandatory = $true)]
    [string]$OutputCsv,
    [int]$DurationSeconds = 60,
    [int]$IntervalSeconds = 5,
    [string[]]$Targets = @("app-1=http://localhost:18080/actuator/prometheus", "app-2=http://localhost:28080/actuator/prometheus")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-MetricValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content,
        [Parameter(Mandatory = $true)]
        [string]$Pattern,
        [switch]$SumAll
    )

    $matches = [regex]::Matches($Content, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($matches.Count -eq 0) {
        return [double]::NaN
    }

    if ($SumAll) {
        $sum = 0.0
        foreach ($match in $matches) {
            $sum += [double]::Parse($match.Groups["value"].Value, [System.Globalization.CultureInfo]::InvariantCulture)
        }
        return $sum
    }

    return [double]::Parse($matches[0].Groups["value"].Value, [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-Sample {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AppName,
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    $content = (Invoke-WebRequest -UseBasicParsing $Url).Content

    [pscustomobject]@{
        timestamp_utc = [DateTime]::UtcNow.ToString("o")
        app = $AppName
        process_cpu_usage = Get-MetricValue -Content $content -Pattern '^process_cpu_usage (?<value>[-0-9.Ee+]+)$'
        system_cpu_usage = Get-MetricValue -Content $content -Pattern '^system_cpu_usage (?<value>[-0-9.Ee+]+)$'
        jvm_threads_live_threads = Get-MetricValue -Content $content -Pattern '^jvm_threads_live_threads (?<value>[-0-9.Ee+]+)$'
        jvm_gc_overhead_percent = Get-MetricValue -Content $content -Pattern '^jvm_gc_overhead_percent (?<value>[-0-9.Ee+]+)$'
        ws_active_connections = Get-MetricValue -Content $content -Pattern '^ws_active_connections (?<value>[-0-9.Ee+]+)$'
        market_ws_queue_delay_seconds_max = Get-MetricValue -Content $content -Pattern '^market_ws_queue_delay_seconds_max (?<value>[-0-9.Ee+]+)$'
        ws_push_duration_seconds_max = Get-MetricValue -Content $content -Pattern '^ws_push_duration_seconds_max (?<value>[-0-9.Ee+]+)$'
        hikaricp_master_active = Get-MetricValue -Content $content -Pattern '^hikaricp_connections_active\{pool="master-pool",\} (?<value>[-0-9.Ee+]+)$'
        hikaricp_slave_active = Get-MetricValue -Content $content -Pattern '^hikaricp_connections_active\{pool="slave-pool",\} (?<value>[-0-9.Ee+]+)$'
        jvm_heap_used_bytes = Get-MetricValue -Content $content -Pattern '^jvm_memory_used_bytes\{area="heap",id="[^"]+",\} (?<value>[-0-9.Ee+]+)$' -SumAll
        jvm_gc_pause_count_total = Get-MetricValue -Content $content -Pattern '^jvm_gc_pause_seconds_count\{.*\} (?<value>[-0-9.Ee+]+)$' -SumAll
        jvm_gc_pause_sum_seconds = Get-MetricValue -Content $content -Pattern '^jvm_gc_pause_seconds_sum\{.*\} (?<value>[-0-9.Ee+]+)$' -SumAll
    }
}

$rows = New-Object System.Collections.Generic.List[object]
$deadline = (Get-Date).AddSeconds($DurationSeconds)

while ((Get-Date) -lt $deadline) {
    foreach ($target in $Targets) {
        $parts = $target -split "=", 2
        if ($parts.Count -ne 2) {
            throw "Invalid target format: $target"
        }
        $rows.Add((Get-Sample -AppName $parts[0] -Url $parts[1]))
    }
    Start-Sleep -Seconds $IntervalSeconds
}

$directory = Split-Path -Parent $OutputCsv
if ($directory) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

$rows | Export-Csv -Path $OutputCsv -NoTypeInformation -Encoding UTF8
