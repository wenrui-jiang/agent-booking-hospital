param(
    [ValidateSet('start','stop','restart','status')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDir = Join-Path $root 'runtime-logs'
$pidDir = Join-Path $logDir 'pids'
$commandDir = Join-Path $logDir 'launch-commands'
$configPath = Join-Path $root 'local-services.json'

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
New-Item -ItemType Directory -Force -Path $pidDir | Out-Null
New-Item -ItemType Directory -Force -Path $commandDir | Out-Null

$backendRoot = Get-ChildItem -Path $root -Recurse -Directory -Filter 'yygh_parent' |
    Select-Object -First 1 -ExpandProperty FullName
$frontendRoot = Get-ChildItem -Path $root -Recurse -Directory -Filter 'yygh-site' |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $backendRoot) {
    throw 'Cannot find backend root directory: yygh_parent'
}
if (-not $frontendRoot) {
    throw 'Cannot find frontend root directory: yygh-site'
}

$java = 'D:\JavaJDK\bin\java.exe'
$maven = 'D:\apache-maven-3.6.1\bin\mvn.cmd'
$nodeCmd = (Get-Command 'npm.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source)

if (-not (Test-Path $java)) {
    throw "Cannot find Java executable: $java"
}
if (-not (Test-Path $maven)) {
    throw "Cannot find Maven executable: $maven"
}
if (-not $nodeCmd -or -not (Test-Path $nodeCmd)) {
    throw "Cannot find npm.cmd in PATH"
}

$env:JAVA_HOME = 'D:\JavaJDK'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$processPath = [System.Environment]::GetEnvironmentVariable('Path', 'Process')
if (-not $processPath) {
    $processPath = [System.Environment]::GetEnvironmentVariable('PATH', 'Process')
}
[System.Environment]::SetEnvironmentVariable('PATH', $null, 'Process')
[System.Environment]::SetEnvironmentVariable('Path', $processPath, 'Process')

function Read-LocalConfig {
    if (Test-Path $configPath) {
        try {
            return Get-Content $configPath -Raw | ConvertFrom-Json
        } catch {
            Write-Warning "Cannot parse local-services.json. Defaults and environment variables will be used."
        }
    }
    return $null
}

function Get-ConfigValue {
    param(
        [object]$Config,
        [string]$PropertyName,
        [string]$EnvironmentName,
        [string]$DefaultValue = ''
    )

    $envValue = [System.Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    if ($envValue) {
        return $envValue
    }
    $envValue = [System.Environment]::GetEnvironmentVariable($EnvironmentName, 'User')
    if ($envValue) {
        return $envValue
    }
    if ($Config -and $Config.PSObject.Properties.Name -contains $PropertyName) {
        return [string]$Config.$PropertyName
    }
    return $DefaultValue
}

$localConfig = Read-LocalConfig
$mailHost = Get-ConfigValue $localConfig 'mailHost' 'YYGH_MAIL_HOST' ''
$mailPort = Get-ConfigValue $localConfig 'mailPort' 'YYGH_MAIL_PORT' ''
$mailUsername = Get-ConfigValue $localConfig 'mailUsername' 'YYGH_MAIL_USERNAME' ''
$mailAuthUsername = Get-ConfigValue $localConfig 'mailAuthUsername' 'YYGH_MAIL_AUTH_USERNAME' ''
$mailFromAddress = Get-ConfigValue $localConfig 'mailFromAddress' 'YYGH_MAIL_FROM_ADDRESS' ''
$mailSsl = Get-ConfigValue $localConfig 'mailSsl' 'YYGH_MAIL_SSL' 'true'
$mailStarttls = Get-ConfigValue $localConfig 'mailStarttls' 'YYGH_MAIL_STARTTLS' 'false'
$mailPassword = Get-ConfigValue $localConfig 'mailPassword' 'YYGH_MAIL_PASSWORD' ''
$mysqlUsername = Get-ConfigValue $localConfig 'mysqlUsername' 'YYGH_MYSQL_USERNAME' 'root'
$mysqlPassword = Get-ConfigValue $localConfig 'mysqlPassword' 'YYGH_MYSQL_PASSWORD' ''
$mailEnabled = if ($mailPassword) { 'true' } else { 'false' }
$msmDevMode = if ($mailPassword) { 'false' } else { 'true' }

function Test-IsWindows {
    return [System.Environment]::OSVersion.Platform -eq 'Win32NT'
}

$services = @(
    @{
        Name = 'service-hosp'
        Port = 8201
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_hosp'
        JarPattern = 'service-hosp*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_hosp?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai'
            "--spring.datasource.username=$mysqlUsername"
            "--spring.datasource.password=$mysqlPassword"
            '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp'
            '--spring.data.mongodb.auto-index-creation=false'
            '--spring.rabbitmq.host=127.0.0.1'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-cmn'
        Port = 8202
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_cmn'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_cmn?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai'
            "--spring.datasource.username=$mysqlUsername"
            "--spring.datasource.password=$mysqlPassword"
            '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp'
            '--spring.data.mongodb.auto-index-creation=false'
            '--spring.rabbitmq.host=127.0.0.1'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-user'
        Port = 8150
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_user'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_user?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai'
            "--spring.datasource.username=$mysqlUsername"
            "--spring.datasource.password=$mysqlPassword"
            '--spring.redis.host=127.0.0.1'
            '--spring.redis.port=6379'
            '--spring.rabbitmq.host=127.0.0.1'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-order'
        Port = 8206
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_order'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/yygh_order?characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai'
            "--spring.datasource.username=$mysqlUsername"
            "--spring.datasource.password=$mysqlPassword"
            '--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/yygh_hosp'
            '--spring.data.mongodb.auto-index-creation=false'
            '--spring.rabbitmq.host=127.0.0.1'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
            '--yygh.order.mock-hospital-submit=true'
        )
    },
    @{
        Name = 'service-msm'
        Port = 8204
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_msm'
        JarPattern = '*.jar'
        Environment = @{
            YYGH_MAIL_PASSWORD = $mailPassword
        }
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.rabbitmq.host=127.0.0.1'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
            '--spring.redis.host=127.0.0.1'
            '--spring.redis.port=6379'
            "--yygh.msm.dev-mode=$msmDevMode"
            '--yygh.msm.dev-code=123456'
            "--yygh.mail.enabled=$mailEnabled"
            "--yygh.mail.host=$mailHost"
            "--yygh.mail.port=$mailPort"
            "--yygh.mail.username=$mailUsername"
            "--yygh.mail.auth-username=$mailAuthUsername"
            "--yygh.mail.from-address=$mailFromAddress"
            "--yygh.mail.ssl=$mailSsl"
            "--yygh.mail.starttls=$mailStarttls"
            '--yygh.mail.from-name=Medical Booking Agent'
        )
    },
    @{
        Name = 'service-oss'
        Port = 8205
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_oss'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-task'
        Port = 8207
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_task'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-statistics'
        Port = 8208
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_statistics'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-agent'
        Port = 8210
        Type = 'java-jar'
        WorkingDirectory = Join-Path $backendRoot 'service\service_agent'
        JarPattern = '*.jar'
        Arguments = @(
            '-jar'
            '__JAR__'
            '--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848'
        )
    },
    @{
        Name = 'service-gateway'
        Port = 80
        Type = 'maven'
        WorkingDirectory = Join-Path $backendRoot 'service_gateway'
        Arguments = @('spring-boot:run')
    },
    @{
        Name = 'yygh-site'
        Port = 3000
        Type = 'npm'
        WorkingDirectory = $frontendRoot
        Environment = @{
            NUXT_BUILD_DIR = ('.nuxt-runtime-' + (Get-Date -Format 'yyyyMMddHHmmss'))
            SKIP_ESLINT = '1'
            NODE_OPTIONS = '--openssl-legacy-provider'
        }
        Arguments = @('run','dev')
    }
)

function Get-PidFilePath {
    param([string]$Name)
    Join-Path $pidDir "$Name.pid"
}

function Get-LogPaths {
    param([string]$Name)
    @{
        Out = Join-Path $logDir "$Name.out.log"
        Err = Join-Path $logDir "$Name.err.log"
    }
}

function Resolve-ServiceJar {
    param($Service)

    Get-ChildItem -Path $Service.WorkingDirectory -Recurse -Filter $Service.JarPattern -File |
        Where-Object { $_.FullName -notmatch '\\original\\' -and $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Assert-ServiceArtifacts {
    $missing = @()
    foreach ($service in $services) {
        if ($service.Type -eq 'java-jar') {
            $jar = Resolve-ServiceJar -Service $service
            if (-not $jar) {
                $missing += "$($service.Name): $($service.WorkingDirectory)"
            }
        } elseif ($service.Type -eq 'maven') {
            if (-not (Test-Path (Join-Path $service.WorkingDirectory 'pom.xml'))) {
                $missing += "$($service.Name): missing pom.xml in $($service.WorkingDirectory)"
            }
        } elseif ($service.Type -eq 'npm') {
            if (-not (Test-Path (Join-Path $service.WorkingDirectory 'package.json'))) {
                $missing += "$($service.Name): missing package.json in $($service.WorkingDirectory)"
            }
        }
    }

    if ($missing.Count -gt 0) {
        throw "Missing service artifacts. Build/install first:`n$($missing -join "`n")"
    }
}

function ConvertTo-CmdArgument {
    param([string]$Value)
    '"' + ($Value -replace '"', '\"') + '"'
}

function ConvertTo-PowerShellSingleQuotedString {
    param([string]$Value)
    "'" + ($Value -replace "'", "''") + "'"
}

function ConvertTo-BatchQuotedString {
    param([string]$Value)
    '"' + ($Value -replace '"', '""') + '"'
}

function Start-ManagedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$StandardOutputPath,
        [string]$StandardErrorPath,
        [hashtable]$Environment
    )

    if (Test-IsWindows) {
        $launcherPath = Join-Path $commandDir "$Name.txt"
        $argumentString = ($ArgumentList | ForEach-Object { ConvertTo-CmdArgument $_ }) -join ' '
        $commandLine = @((ConvertTo-PowerShellSingleQuotedString $FilePath)) + ($ArgumentList | ForEach-Object { ConvertTo-PowerShellSingleQuotedString $_ })
        Set-Content -LiteralPath $launcherPath -Value (($commandLine -join ' ') + [Environment]::NewLine + "WorkingDirectory: $WorkingDirectory") -Encoding UTF8

        $previousEnvironment = @{}
        if ($Environment) {
            foreach ($key in $Environment.Keys) {
                $previousEnvironment[$key] = [System.Environment]::GetEnvironmentVariable($key, 'Process')
                [System.Environment]::SetEnvironmentVariable($key, [string]$Environment[$key], 'Process')
            }
        }

        try {
            $process = Start-Process -FilePath $FilePath `
                -ArgumentList $argumentString `
                -WorkingDirectory $WorkingDirectory `
                -RedirectStandardOutput $StandardOutputPath `
                -RedirectStandardError $StandardErrorPath `
                -WindowStyle Hidden `
                -PassThru
            if (-not $process) {
                throw "Failed to start process: $FilePath"
            }
            return [int]$process.Id
        } finally {
            foreach ($key in $previousEnvironment.Keys) {
                [System.Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
            }
        }

        if ($Name -eq 'yygh-site') {
            $scriptLines = @('$ErrorActionPreference = ''Continue''')
            $scriptLines += 'try {'
            $scriptLines += 'Set-Location -LiteralPath ' + (ConvertTo-PowerShellSingleQuotedString $WorkingDirectory)
            if ($Environment) {
                foreach ($key in $Environment.Keys) {
                    $scriptLines += '$env:' + $key + ' = ' + (ConvertTo-PowerShellSingleQuotedString ([string]$Environment[$key]))
                }
            }
            $npm = ConvertTo-PowerShellSingleQuotedString $nodeCmd
            $outPath = ConvertTo-PowerShellSingleQuotedString $StandardOutputPath
            $errPath = ConvertTo-PowerShellSingleQuotedString $StandardErrorPath
            $scriptLines += "& $npm 'run' 'build' > $outPath 2> $errPath"
            $scriptLines += 'if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }'
            $scriptLines += "& $npm 'run' 'start' >> $outPath 2>> $errPath"
            $scriptLines += 'exit $LASTEXITCODE'
            $scriptLines += '} catch {'
            $scriptLines += '  $_ | Out-String | Add-Content -LiteralPath ' + (ConvertTo-PowerShellSingleQuotedString $StandardErrorPath)
            $scriptLines += '  exit 1'
            $scriptLines += '}'
            $encodedCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes(($scriptLines -join [Environment]::NewLine)))

            $launcherPath = Join-Path $commandDir "$Name.txt"
            Set-Content -LiteralPath $launcherPath -Value (($scriptLines -join [Environment]::NewLine) + [Environment]::NewLine + "WorkingDirectory: $WorkingDirectory") -Encoding UTF8

            $processInfo = New-Object System.Diagnostics.ProcessStartInfo
            $processInfo.FileName = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
            $processInfo.Arguments = '-NoProfile -ExecutionPolicy Bypass -EncodedCommand ' + $encodedCommand
            $processInfo.UseShellExecute = $true
            $processInfo.CreateNoWindow = $true
            $processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden

            $process = [System.Diagnostics.Process]::Start($processInfo)
            if (-not $process) {
                throw "Failed to start process: $FilePath"
            }
            return [int]$process.Id
        }

        if ($true) {
        $scriptLines = @('$ErrorActionPreference = ''Continue''')
        $scriptLines += 'try {'
        $scriptLines += 'Set-Location -LiteralPath ' + (ConvertTo-PowerShellSingleQuotedString $WorkingDirectory)
        if ($Environment) {
            foreach ($key in $Environment.Keys) {
                if ($key -eq 'YYGH_MAIL_PASSWORD') {
                    $scriptLines += '$env:YYGH_MAIL_PASSWORD = (Get-Content -LiteralPath ' + (ConvertTo-PowerShellSingleQuotedString $configPath) + ' -Raw | ConvertFrom-Json).mailPassword'
                } else {
                    $scriptLines += '$env:' + $key + ' = ' + (ConvertTo-PowerShellSingleQuotedString ([string]$Environment[$key]))
                }
            }
        }
        $command = '& ' + (ConvertTo-PowerShellSingleQuotedString $FilePath)
        foreach ($argument in $ArgumentList) {
            $command += ' ' + (ConvertTo-PowerShellSingleQuotedString $argument)
        }
        $outPath = ConvertTo-PowerShellSingleQuotedString $StandardOutputPath
        $errPath = ConvertTo-PowerShellSingleQuotedString $StandardErrorPath
        $scriptLines += "$command > $outPath 2> $errPath"
        $scriptLines += 'exit $LASTEXITCODE'
        $scriptLines += '} catch {'
        $scriptLines += '  $_ | Out-String | Add-Content -LiteralPath ' + (ConvertTo-PowerShellSingleQuotedString $StandardErrorPath)
        $scriptLines += '  exit 1'
        $scriptLines += '}'
        $scriptPath = Join-Path $commandDir "$Name.ps1"
        Set-Content -LiteralPath $scriptPath -Value ($scriptLines -join [Environment]::NewLine) -Encoding UTF8

        $launcherPath = Join-Path $commandDir "$Name.txt"
        $safeScriptLines = $scriptLines | ForEach-Object {
            if ($_ -match '^\$env:YYGH_MAIL_PASSWORD\s*=') { '$env:YYGH_MAIL_PASSWORD = ''<redacted>''' } else { $_ }
        }
        Set-Content -LiteralPath $launcherPath -Value (($safeScriptLines -join [Environment]::NewLine) + [Environment]::NewLine + "WorkingDirectory: $WorkingDirectory") -Encoding UTF8

        $processInfo = New-Object System.Diagnostics.ProcessStartInfo
        $processInfo.FileName = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
        $processInfo.Arguments = '-NoProfile -ExecutionPolicy Bypass -File ' + (ConvertTo-CmdArgument $scriptPath)
        $processInfo.UseShellExecute = $true
        $processInfo.CreateNoWindow = $true
        $processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden

        $process = [System.Diagnostics.Process]::Start($processInfo)
        if (-not $process) {
            throw "Failed to start process: $FilePath"
        }
        return [int]$process.Id
        }

        $launcherPath = Join-Path $commandDir "$Name.txt"
        $commandLine = @((ConvertTo-PowerShellSingleQuotedString $FilePath)) + ($ArgumentList | ForEach-Object { ConvertTo-PowerShellSingleQuotedString $_ })
        Set-Content -LiteralPath $launcherPath -Value (($commandLine -join ' ') + [Environment]::NewLine + "WorkingDirectory: $WorkingDirectory") -Encoding UTF8

        $previousEnvironment = @{}
        if ($Environment) {
            foreach ($key in $Environment.Keys) {
                $previousEnvironment[$key] = [System.Environment]::GetEnvironmentVariable($key, 'Process')
                [System.Environment]::SetEnvironmentVariable($key, [string]$Environment[$key], 'Process')
            }
        }

        try {
            $process = Start-Process -FilePath $FilePath `
                -ArgumentList $ArgumentList `
                -WorkingDirectory $WorkingDirectory `
                -RedirectStandardOutput $StandardOutputPath `
                -RedirectStandardError $StandardErrorPath `
                -WindowStyle Hidden `
                -PassThru
            if (-not $process) {
                throw "Failed to start process: $FilePath"
            }
            return [int]$process.Id
        } finally {
            foreach ($key in $previousEnvironment.Keys) {
                [System.Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
            }
        }
    }

    $proc = Start-Process -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $StandardOutputPath `
        -RedirectStandardError $StandardErrorPath `
        -WindowStyle Hidden `
        -PassThru
    return [int]$proc.Id
}

function Get-ServiceProcessId {
    param([string]$Name)
    $pidFile = Get-PidFilePath -Name $Name
    if (Test-Path $pidFile) {
        $servicePid = [int](Get-Content $pidFile -Raw)
        $process = Get-Process -Id $servicePid -ErrorAction SilentlyContinue
        if ($process) {
            $allowedProcessNames = @('cmd', 'java', 'javaw', 'node', 'npm', 'powershell', 'pwsh')
            $isManaged = (-not (Test-IsWindows)) -or ($allowedProcessNames -contains $process.ProcessName)
            if ($isManaged) {
                return $servicePid
            }
        }
        Remove-Item $pidFile -Force
    }
    return $null
}

function Get-ListeningProcessIdByPort {
    param([int]$Port)
    if (-not $Port) {
        return $null
    }

    $lines = & netstat.exe -ano 2>$null | Select-String -Pattern "LISTENING"
    foreach ($line in $lines) {
        $parts = ($line.ToString().Trim() -split '\s+')
        if ($parts.Count -ge 5 -and $parts[1] -match "[:.]$Port$") {
            $listenerPid = [int]$parts[-1]
            $listenerProcess = Get-Process -Id $listenerPid -ErrorAction SilentlyContinue
            $allowedProcessNames = @('cmd', 'java', 'javaw', 'node', 'npm', 'powershell', 'pwsh')
            if ($listenerProcess -and ($allowedProcessNames -contains $listenerProcess.ProcessName)) {
                return $listenerPid
            }
        }
    }
    return $null
}

function Stop-ServiceByName {
    param(
        [string]$Name,
        [int]$Port
    )
    $servicePid = Get-ServiceProcessId -Name $Name
    if (-not $servicePid) {
        $servicePid = Get-ListeningProcessIdByPort -Port $Port
    }
    if ($servicePid) {
        if (Test-IsWindows) {
            & taskkill.exe /PID $servicePid /T /F | Out-Null
        } else {
            Stop-Process -Id $servicePid -Force
        }
        Remove-Item (Get-PidFilePath -Name $Name) -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped $Name (PID $servicePid)"
    } else {
        Write-Host "$Name is not running"
    }
}

function Start-OneService {
    param($Service)

    $existingPid = Get-ServiceProcessId -Name $Service.Name
    if ($existingPid) {
        Write-Host "$($Service.Name) already running (PID $existingPid)"
        return
    }

    $logs = Get-LogPaths -Name $Service.Name
    Set-Content -LiteralPath $logs.Out -Value '' -Encoding UTF8
    Set-Content -LiteralPath $logs.Err -Value '' -Encoding UTF8
    $filePath = $null
    $argumentList = $null
    $workingDirectory = $Service.WorkingDirectory

    switch ($Service.Type) {
        'java-jar' {
            $jar = Resolve-ServiceJar -Service $Service
            if (-not $jar) {
                throw "Cannot find jar for $($Service.Name) under $($Service.WorkingDirectory). Build it first."
            }
            $argumentList = @($Service.Arguments |
                Where-Object { $_ -notmatch '=$' } |
                ForEach-Object { if ($_ -eq '__JAR__') { $jar.FullName } else { $_ } })
            $filePath = $java
            $workingDirectory = $root
        }
        'npm' {
            $filePath = $nodeCmd
            $argumentList = $Service.Arguments
            if ($Service.Name -eq 'yygh-site') {
                $previousEnvironment = @{}
                if ($Service.Environment) {
                    foreach ($key in $Service.Environment.Keys) {
                        $previousEnvironment[$key] = [System.Environment]::GetEnvironmentVariable($key, 'Process')
                        [System.Environment]::SetEnvironmentVariable($key, [string]$Service.Environment[$key], 'Process')
                    }
                }
                Push-Location -LiteralPath $workingDirectory
                try {
                    $buildProcess = Start-Process -FilePath $nodeCmd `
                        -ArgumentList 'run build' `
                        -WorkingDirectory $workingDirectory `
                        -RedirectStandardOutput $logs.Out `
                        -RedirectStandardError $logs.Err `
                        -WindowStyle Hidden `
                        -Wait `
                        -PassThru
                    if ($buildProcess.ExitCode -ne 0) {
                        throw "Frontend build failed. See $($logs.Out)"
                    }
                } finally {
                    Pop-Location
                    foreach ($key in $previousEnvironment.Keys) {
                        [System.Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
                    }
                }
                $argumentList = @('run', 'start')
            }
        }
        'maven' {
            $filePath = $maven
            $argumentList = $Service.Arguments
        }
        default {
            throw "Unsupported service type: $($Service.Type)"
        }
    }

    $processId = Start-ManagedProcess `
        -Name $Service.Name `
        -FilePath $filePath `
        -ArgumentList $argumentList `
        -WorkingDirectory $workingDirectory `
        -StandardOutputPath $logs.Out `
        -StandardErrorPath $logs.Err `
        -Environment $Service.Environment
    Set-Content -Path (Get-PidFilePath -Name $Service.Name) -Value $processId -NoNewline
    Write-Host "Started $($Service.Name) (PID $processId)"
}

function Show-Status {
    foreach ($service in $services) {
        $servicePid = Get-ServiceProcessId -Name $service.Name
        if ($servicePid) {
            Write-Host "$($service.Name): running (PID $servicePid)"
        } else {
            Write-Host "$($service.Name): stopped"
        }
    }
}

switch ($Action) {
    'start' {
        Assert-ServiceArtifacts
        foreach ($service in $services) {
            Start-OneService -Service $service
        }
    }
    'stop' {
        foreach ($service in $services) {
            Stop-ServiceByName -Name $service.Name -Port $service.Port
        }
    }
    'restart' {
        foreach ($service in $services) {
            Stop-ServiceByName -Name $service.Name -Port $service.Port
        }
        Start-Sleep -Seconds 2
        Assert-ServiceArtifacts
        foreach ($service in $services) {
            Start-OneService -Service $service
        }
    }
    'status' {
        Show-Status
    }
}
