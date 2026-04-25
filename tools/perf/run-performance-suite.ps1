param(
    [string]$Tag = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$BypassKey = "k6-bypass-20260420",
    [string]$NetworkName = "stock-simulation_stock-network",
    [string]$BaseUrl = "http://nginx",
    [string]$WsUrl = "ws://nginx/ws/market-native",
    [switch]$SkipUpperBound,
    [switch]$SkipCommonQps,
    [switch]$SkipWebSocket
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$k6WorkDir = (Resolve-Path (Join-Path $repoRoot "k6")).Path
$runRoot = Join-Path $repoRoot ("tools\perf\runs\{0}" -f $Tag)
New-Item -ItemType Directory -Force -Path $runRoot | Out-Null

function Invoke-ActuatorSampler {
    param([string]$OutputCsv, [int]$DurationSeconds = 75)

    Start-Process powershell -ArgumentList @(
        "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $repoRoot "tools\perf\collect-actuator-samples.ps1"),
        "-OutputCsv", $OutputCsv,
        "-DurationSeconds", $DurationSeconds,
        "-IntervalSeconds", "5"
    ) -PassThru
}

function Wait-IfRunning {
    param($Process)

    if ($null -ne $Process -and (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) {
        Wait-Process -Id $Process.Id
    }
}

function Invoke-K6Scenario {
    param(
        [string]$Name,
        [string]$ScriptFile,
        [hashtable]$EnvMap,
        [switch]$CollectActuator
    )

    $stdoutPath = Join-Path $runRoot ("{0}.txt" -f $Name)
    $actuatorPath = Join-Path $runRoot ("{0}-actuator.csv" -f $Name)
    $summaryExport = "/work/_{0}-{1}.json" -f $Name, $Tag

    $sampler = $null
    if ($CollectActuator) {
        $sampler = Invoke-ActuatorSampler -OutputCsv $actuatorPath
    }

    $dockerArgs = @(
        "run", "--rm",
        "--network", $NetworkName,
        "-v", ("{0}:/work" -f $k6WorkDir),
        "-w", "/work",
        "grafana/k6:1.7.1",
        "run",
        ("/work/{0}" -f $ScriptFile)
    )

    foreach ($key in ($EnvMap.Keys | Sort-Object)) {
        $dockerArgs += @("-e", ("{0}={1}" -f $key, $EnvMap[$key]))
    }
    $dockerArgs += @("--summary-export", $summaryExport)

    try {
        & docker @dockerArgs | Tee-Object -FilePath $stdoutPath
    } finally {
        Wait-IfRunning -Process $sampler
    }
}

Invoke-K6Scenario -Name "warmup" -ScriptFile "perf-fullchain-and-endpoints.js" -EnvMap @{
    BASE_URL = $BaseUrl
    K6_BYPASS_KEY = $BypassKey
    ACCEPT_429 = "true"
    DURATION = "20s"
    FULL_CHAIN_RPS = "600"
    QUOTE_RPS = "240"
    PORTFOLIO_RPS = "120"
    TRADE_LIST_RPS = "120"
}

Invoke-K6Scenario -Name "recommended" -ScriptFile "perf-fullchain-and-endpoints.js" -CollectActuator -EnvMap @{
    BASE_URL = $BaseUrl
    K6_BYPASS_KEY = $BypassKey
    ACCEPT_429 = "true"
    DURATION = "60s"
    FULL_CHAIN_RPS = "650"
    QUOTE_RPS = "260"
    PORTFOLIO_RPS = "130"
    TRADE_LIST_RPS = "130"
}

if (-not $SkipUpperBound) {
    Invoke-K6Scenario -Name "upper-bound" -ScriptFile "perf-fullchain-and-endpoints.js" -CollectActuator -EnvMap @{
        BASE_URL = $BaseUrl
        K6_BYPASS_KEY = $BypassKey
        ACCEPT_429 = "true"
        DURATION = "40s"
        FULL_CHAIN_RPS = "800"
        QUOTE_RPS = "320"
        PORTFOLIO_RPS = "160"
        TRADE_LIST_RPS = "160"
    }
}

if (-not $SkipCommonQps) {
    Invoke-K6Scenario -Name "common-qps" -ScriptFile "perf-endpoints-common-qps.js" -EnvMap @{
        BASE_URL = $BaseUrl
        K6_BYPASS_KEY = $BypassKey
        ACCEPT_429 = "true"
        DURATION = "60s"
    }
}

if (-not $SkipWebSocket) {
    Invoke-K6Scenario -Name "websocket" -ScriptFile "websocket-load-test.js" -CollectActuator -EnvMap @{
        BASE_URL = $BaseUrl
        K6_BYPASS_KEY = $BypassKey
        DURATION = "60s"
        VUS = "500"
        WS_URL = $WsUrl
        TARGET_CODE = "sh600519"
        WS_SESSION_MS = "60000"
        WS_HEARTBEAT_MS = "10000"
    }
}
