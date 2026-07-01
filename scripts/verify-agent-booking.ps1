param(
    [string]$SessionId = ("verify-" + [DateTimeOffset]::Now.ToUnixTimeMilliseconds())
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendRoot = Get-ChildItem -Path $root -Recurse -Directory -Filter 'yygh_parent' |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $backendRoot) {
    throw 'Cannot find backend root directory: yygh_parent'
}
$logDir = Join-Path $root 'runtime-logs\verify-agent'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$mysqlUsername = if ($env:YYGH_MYSQL_USERNAME) { $env:YYGH_MYSQL_USERNAME } else { 'root' }
$mysqlPassword = if ($env:YYGH_MYSQL_PASSWORD) { $env:YYGH_MYSQL_PASSWORD } else { '' }

$java = 'D:\JavaJDK\bin\java.exe'
$env:JAVA_HOME = 'D:\JavaJDK'
$processPath = [System.Environment]::GetEnvironmentVariable('Path', 'Process')
if (-not $processPath) {
    $processPath = [System.Environment]::GetEnvironmentVariable('PATH', 'Process')
}
[System.Environment]::SetEnvironmentVariable('PATH', $null, 'Process')
[System.Environment]::SetEnvironmentVariable('Path', "$env:JAVA_HOME\bin;$processPath", 'Process')

function Stop-Port {
    param([int]$Port)
    Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object {
            if ($_ -and $_ -ne $PID) {
                Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
            }
        }
}

function Wait-Port {
    param([int]$Port, [int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort $Port) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Port $Port did not become ready in $TimeoutSeconds seconds"
}

function Test-TcpPort {
    param([int]$Port)
    $client = New-Object Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1000, $false)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-ShortPath {
    param([string]$Path)
    $resolved = (Resolve-Path $Path).Path
    $escaped = $resolved -replace '"', '\"'
    $short = cmd.exe /c "for %I in (`"$escaped`") do @echo %~sI"
    if ($LASTEXITCODE -ne 0 -or -not $short) {
        return $resolved
    }
    return ($short | Select-Object -First 1).Trim()
}

function Find-ServiceJar {
    param([string]$ServiceDir)
    $jar = Get-ChildItem -Path (Join-Path $backendRoot "service\$ServiceDir\target") -File -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) {
        throw "Cannot find jar for $ServiceDir"
    }
    return $jar.FullName
}

function Start-Jar {
    param(
        [string]$Name,
        [string]$Jar,
        [string[]]$Args
    )
    $out = Join-Path $logDir "$Name.out.log"
    $err = Join-Path $logDir "$Name.err.log"
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    $shortJar = Get-ShortPath $Jar
    $allArgs = @('-jar', $shortJar) + $Args
    $argumentLine = ($allArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
    Start-Process -FilePath $java `
        -ArgumentList $argumentLine `
        -WorkingDirectory $root `
        -RedirectStandardOutput $out `
        -RedirectStandardError $err `
        -WindowStyle Hidden `
        -PassThru
}

function Invoke-AgentChat {
    param([string]$Message)
    $body = @{
        sessionId = $SessionId
        message = $Message
    } | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    Invoke-RestMethod -Method Post `
        -Uri 'http://127.0.0.1:8210/api/agent/chat' `
        -Body $bytes `
        -ContentType 'application/json; charset=utf-8'
}

function Decode-Utf8Text {
    param([string]$Base64)
    [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Base64))
}

$started = @()
$startedNacos = $false
try {
    8150, 8201, 8202, 8206, 8210 | ForEach-Object { Stop-Port $_ }
    Start-Sleep -Seconds 2

    if (-not (Test-TcpPort 8848)) {
        $nacosZip = Get-ChildItem -Path $root -Recurse -File -Filter 'nacos-server-1.1.4.zip' | Select-Object -First 1
        if (-not $nacosZip) {
            throw 'Nacos 8848 is not listening and nacos-server-1.1.4.zip was not found.'
        }
        $toolsDir = Join-Path $root 'runtime-tools'
        $nacosDir = Join-Path $toolsDir 'nacos'
        $startup = Join-Path $nacosDir 'bin\startup.cmd'
        if (-not (Test-Path $startup)) {
            New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
            Expand-Archive -Path $nacosZip.FullName -DestinationPath $toolsDir -Force
            $expanded = Get-ChildItem -Path $toolsDir -Directory -Filter 'nacos*' | Select-Object -First 1
            if ($expanded -and $expanded.FullName -ne $nacosDir) {
                if (Test-Path $nacosDir) {
                    Remove-Item -Path $nacosDir -Recurse -Force
                }
                Move-Item -Path $expanded.FullName -Destination $nacosDir
            }
        }
        if (-not (Test-Path $startup)) {
            throw "Cannot find Nacos startup script: $startup"
        }
        $nacosCommand = "set JAVA_HOME=$env:JAVA_HOME&&set PATH=$env:JAVA_HOME\bin;%PATH%&&`"$startup`" -m standalone"
        Start-Process -FilePath 'cmd.exe' `
            -ArgumentList @('/c', $nacosCommand) `
            -WorkingDirectory (Join-Path $nacosDir 'bin') `
            -WindowStyle Hidden | Out-Null
        $startedNacos = $true
        Wait-Port 8848 120
    }

    $started += Start-Jar 'service-cmn' `
        (Find-ServiceJar 'service_cmn') `
        @(
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_cmn?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai',
            "--spring.datasource.username=$mysqlUsername",
            "--spring.datasource.password=$mysqlPassword",
            '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp',
            '--spring.rabbitmq.host=127.0.0.1',
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    Wait-Port 8202 120

    $started += Start-Jar 'service-hosp' `
        (Find-ServiceJar 'service_hosp') `
        @(
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_hosp?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai',
            "--spring.datasource.username=$mysqlUsername",
            "--spring.datasource.password=$mysqlPassword",
            '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp',
            '--spring.rabbitmq.host=127.0.0.1',
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    Wait-Port 8201 150

    $started += Start-Jar 'service-user' `
        (Find-ServiceJar 'service_user') `
        @(
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_user?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai',
            "--spring.datasource.username=$mysqlUsername",
            "--spring.datasource.password=$mysqlPassword",
            '--spring.rabbitmq.host=127.0.0.1',
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    Wait-Port 8150 120

    $started += Start-Jar 'service-order' `
        (Find-ServiceJar 'service_order') `
        @(
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_order?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai',
            "--spring.datasource.username=$mysqlUsername",
            "--spring.datasource.password=$mysqlPassword",
            '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp',
            '--spring.rabbitmq.host=127.0.0.1',
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848',
            '--yygh.order.mock-hospital-submit=true'
        )
    Wait-Port 8206 150

    $started += Start-Jar 'service-agent' `
        (Find-ServiceJar 'service_agent') `
        @('--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848')
    Wait-Port 8210 120
    Start-Sleep -Seconds 8

    $hosp = Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8201/api/hosp/hospital/findHospList/1/5'
    $rules = Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8201/api/hosp/hospital/auth/getBookingScheduleRule/1/7/1000_0/200041246'
    $first = Invoke-AgentChat (Decode-Utf8Text '5oiR5ZKz5Ze95ZeT5a2Q55a85LiJ5aSp77yM5pyJ54K55Y+R54Ot')
    $second = Invoke-AgentChat (Decode-Utf8Text '56Gu6K6k56eR5a6k5bm25p+l5Y+3')
    $third = Invoke-AgentChat (Decode-Utf8Text '56Gu6K6k5oyC5Y+3')
    $tools = Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:8210/api/agent/tool-calls/" + $SessionId)

    [PSCustomObject]@{
        sessionId = $SessionId
        hospitalCount = $hosp.data.totalElements
        scheduleDays = $rules.data.bookingScheduleList.Count
        firstCode = $first.code
        firstStage = $first.data.stage
        secondCode = $second.code
        secondStage = $second.data.stage
        secondAnswer = $second.data.answer
        thirdCode = $third.code
        thirdStage = $third.data.stage
        thirdAnswer = $third.data.answer
        toolCalls = $tools.data.Count
        failedTools = @($tools.data | Where-Object { $_.status -ne 'SUCCESS' }).Count
    } | ConvertTo-Json -Depth 8
} finally {
    foreach ($proc in $started) {
        if ($proc -and -not $proc.HasExited) {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($startedNacos) {
        Stop-Port 8848
    }
}
