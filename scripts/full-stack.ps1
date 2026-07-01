param(
    [ValidateSet('start','restart','status','stop')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDir = Join-Path $root 'runtime-logs'
$configPath = Join-Path $root 'local-services.json'
$managerScript = Join-Path $PSScriptRoot 'manage-services.ps1'

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

function Write-Ok {
    param([string]$Message)
    Write-Host "[OK] $Message"
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] $Message"
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[FAIL] $Message"
}

function Test-Port {
    param([int]$Port)
    foreach ($hostName in @('127.0.0.1', '::1', 'localhost')) {
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $async = $client.BeginConnect($hostName, $Port, $null, $null)
            $success = $async.AsyncWaitHandle.WaitOne(1000, $false)
            if ($success) {
                $client.EndConnect($async)
                $client.Close()
                return $true
            }
            $client.Close()
        } catch {
            try {
                if ($client) {
                    $client.Close()
                }
            } catch {
            }
        }
    }
    $listeners = & netstat.exe -ano 2>$null | Select-String -Pattern 'LISTENING'
    foreach ($listener in $listeners) {
        $parts = ($listener.ToString().Trim() -split '\s+')
        if ($parts.Count -ge 5 -and $parts[1] -match "[:.]$Port$") {
            return $true
        }
    }
    return $false
}

function Wait-Port {
    param(
        [string]$Name,
        [int]$Port,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port -Port $Port) {
            Write-Ok "$Name is ready on port $Port"
            return $true
        }
        Start-Sleep -Seconds 2
    }
    Write-Fail "$Name did not become ready on port $Port within ${TimeoutSeconds}s"
    return $false
}

function Find-FirstPath {
    param([string[]]$Candidates)
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

function Read-LocalConfig {
    if (Test-Path $configPath) {
        try {
            return Get-Content $configPath -Raw | ConvertFrom-Json
        } catch {
            Write-Warn "Cannot parse local-services.json. Auto-detection will be used."
        }
    }
    return $null
}

function Resolve-ToolPaths {
    $cfg = Read-LocalConfig
    $javaFromEnv = $null
    if ($env:JAVA_HOME) {
        $javaFromEnv = Join-Path $env:JAVA_HOME 'bin\java.exe'
    }

    $paths = [ordered]@{
        Java = Find-FirstPath @(
            $cfg.java,
            $javaFromEnv,
            'D:\JavaJDK\bin\java.exe',
            'C:\JavaJDK\bin\java.exe'
        )
        Redis = Find-FirstPath @(
            $cfg.redis,
            'D:\Redis\Redis-x64-3.0.504\redis-server.exe',
            'C:\Redis\redis-server.exe'
        )
        RedisConf = Find-FirstPath @(
            $cfg.redisConf,
            'D:\Redis\Redis-x64-3.0.504\redis.windows.conf',
            'C:\Redis\redis.windows.conf'
        )
        NacosStartup = Find-FirstPath @(
            $cfg.nacosStartup,
            'D:\Nacos\bin\startup.cmd',
            'C:\Nacos\bin\startup.cmd'
        )
        Mongo = Find-FirstPath @(
            $cfg.mongo,
            'D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin\mongod.exe',
            'C:\MongoDB\bin\mongod.exe'
        )
        MongoShell = Find-FirstPath @(
            $cfg.mongoShell,
            'D:\MongoDB\mongodb-win32-x86_64-windows-4.4.30\bin\mongo.exe',
            'C:\MongoDB\bin\mongo.exe',
            'D:\MongoDB\bin\mongosh.exe',
            'C:\MongoDB\bin\mongosh.exe'
        )
        MongoDbPath = Find-FirstPath @(
            $cfg.mongoDbPath,
            'D:\MongoDB\data\db',
            'C:\MongoDB\data\db'
        )
        MongoLog = if ($cfg.mongoLog) { $cfg.mongoLog } else { 'D:\MongoDB\log\mongod.log' }
        MySQLService = if ($cfg.mysqlService) { $cfg.mysqlService } else { 'MySQL' }
        Rabbit = Find-FirstPath @(
            $cfg.rabbit,
            'D:\Rabbitmq\rabbitmq_server-3.11.23\sbin\rabbitmq-server.bat',
            'C:\Rabbitmq\rabbitmq_server-3.11.23\sbin\rabbitmq-server.bat'
        )
        ErlangHome = Find-FirstPath @(
            $cfg.erlangHome,
            'D:\ErlangOTP',
            'C:\ErlangOTP'
        )
        RabbitBase = if ($cfg.rabbitBase) { $cfg.rabbitBase } else { 'D:\Rabbitmq\rabbitmq_base' }
    }

    return $paths
}

function Start-MySQL {
    param([string]$ServiceName = 'MySQL')

    if (Test-Port 3306) {
        Write-Ok 'MySQL already running'
        return
    }

    $service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if (-not $service) {
        $service = Get-Service -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^mysql' -or $_.DisplayName -match 'MySQL' } |
            Select-Object -First 1
    }

    if (-not $service) {
        throw 'MySQL is not running and no MySQL Windows service was found. Install/start MySQL or set mysqlService in local-services.json.'
    }

    if ($service.Status -ne 'Running') {
        Write-Host "Starting MySQL service: $($service.Name)"
        Start-Service -Name $service.Name
    }

    Wait-Port 'MySQL' 3306 60 | Out-Null
    if (-not (Test-Port 3306)) {
        throw 'MySQL service was started but port 3306 is still closed.'
    }
}

function Show-DetectedPath {
    param(
        [string]$Name,
        [string]$Path
    )
    if ($Path) {
        Write-Ok "Detected ${Name}: $Path"
    } else {
        Write-Fail "Cannot detect ${Name}"
    }
}

function Start-Middleware {
    $paths = Resolve-ToolPaths

    Write-Step "Detect local middleware paths"
    Show-DetectedPath 'Java' $paths.Java
    Show-DetectedPath 'Redis' $paths.Redis
    Show-DetectedPath 'Nacos' $paths.NacosStartup
    Show-DetectedPath 'MongoDB' $paths.Mongo
    Show-DetectedPath 'RabbitMQ' $paths.Rabbit
    Show-DetectedPath 'Erlang' $paths.ErlangHome

    $missing = @()
    foreach ($key in @('Java','Redis','RedisConf','NacosStartup','Mongo','MongoDbPath','Rabbit','ErlangHome')) {
        if (-not $paths[$key]) {
            $missing += $key
        }
    }
    if ($missing.Count -gt 0) {
        throw "Missing local dependencies: $($missing -join ', '). Create local-services.json or install them in a known path."
    }

    Write-Step "Start database and middleware services"

    Start-MySQL -ServiceName $paths.MySQLService

    if (Test-Port 6379) {
        Write-Ok 'Redis already running'
    } else {
        Start-Process -FilePath $paths.Redis -ArgumentList $paths.RedisConf -WorkingDirectory (Split-Path $paths.Redis) -WindowStyle Hidden
        Wait-Port 'Redis' 6379 30 | Out-Null
    }

    if (Test-Port 8848) {
        Write-Ok 'Nacos already running'
    } else {
        $nacosBin = Split-Path $paths.NacosStartup
        $cmd = "set JAVA_HOME=$($paths.Java | Split-Path | Split-Path)&&set PATH=$($paths.Java | Split-Path);%PATH%&&startup.cmd -m standalone"
        Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $cmd -WorkingDirectory $nacosBin -WindowStyle Hidden
        Wait-Port 'Nacos' 8848 90 | Out-Null
    }

    if (Test-Port 27017) {
        Write-Ok 'MongoDB already running'
    } else {
        $mongoLogDir = Split-Path $paths.MongoLog
        New-Item -ItemType Directory -Force -Path $mongoLogDir | Out-Null
        Start-Process -FilePath $paths.Mongo -ArgumentList '--dbpath', $paths.MongoDbPath, '--logpath', $paths.MongoLog, '--logappend', '--bind_ip', '127.0.0.1', '--port', '27017' -WorkingDirectory (Split-Path $paths.Mongo) -WindowStyle Hidden
        Wait-Port 'MongoDB' 27017 60 | Out-Null
    }

    if (Test-Port 5672) {
        Write-Ok 'RabbitMQ already running'
    } else {
        $env:ERLANG_HOME = $paths.ErlangHome
        $env:RABBITMQ_SERVER = (Split-Path (Split-Path $paths.Rabbit))
        $env:RABBITMQ_BASE = $paths.RabbitBase
        $env:Path = "$env:ERLANG_HOME\bin;$env:RABBITMQ_SERVER\sbin;$env:Path"
        & $paths.Rabbit -detached
        Wait-Port 'RabbitMQ' 5672 90 | Out-Null
    }
}

function Show-MiddlewareStatus {
    Write-Step "Middleware status"
    foreach ($item in @(
        @{Name='MySQL'; Port=3306},
        @{Name='Redis'; Port=6379},
        @{Name='Nacos'; Port=8848},
        @{Name='MongoDB'; Port=27017},
        @{Name='RabbitMQ'; Port=5672},
        @{Name='RabbitMQ Management'; Port=15672}
    )) {
        if (Test-Port $item.Port) {
            Write-Ok "$($item.Name) port $($item.Port) is open"
        } else {
            Write-Fail "$($item.Name) port $($item.Port) is closed"
        }
    }
}

function Get-ServiceProcessId {
    param([string]$Name)
    $pidPath = Join-Path (Join-Path $logDir 'pids') "$Name.pid"
    if (-not (Test-Path $pidPath)) {
        return $null
    }

    try {
        $servicePid = [int](Get-Content $pidPath -Raw)
        $process = Get-Process -Id $servicePid -ErrorAction SilentlyContinue
        if ($process) {
            return $servicePid
        }
    } catch {
        return $null
    }
    return $null
}

function Write-ServiceLogTail {
    param(
        [string]$Name,
        [int]$Lines = 60
    )

    foreach ($suffix in @('err', 'out')) {
        $path = Join-Path $logDir "$Name.$suffix.log"
        if (Test-Path $path) {
            Write-Host ""
            Write-Host "----- $Name $suffix log tail -----"
            Get-Content -LiteralPath $path -Tail $Lines -ErrorAction SilentlyContinue | ForEach-Object {
                Write-Host $_
            }
        }
    }
}

function Wait-ServicePort {
    param(
        [string]$Name,
        [int]$Port,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port -Port $Port) {
            Write-Ok "$Name is ready on port $Port"
            return $true
        }

        if (-not (Get-ServiceProcessId -Name $Name)) {
            Write-Fail "$Name exited before port $Port became ready"
            Write-ServiceLogTail -Name $Name
            return $false
        }

        Start-Sleep -Seconds 2
    }

    Write-Fail "$Name did not become ready on port $Port within ${TimeoutSeconds}s"
    Write-ServiceLogTail -Name $Name -Lines 40
    return $false
}

function Repair-MongoIndexes {
    $paths = Resolve-ToolPaths
    if (-not $paths.MongoShell) {
        Write-Warn 'Mongo shell was not detected; skip Mongo index repair.'
        return
    }
    if (-not (Test-Port 27017)) {
        Write-Warn 'MongoDB is not ready; skip Mongo index repair.'
        return
    }

    Write-Step "Repair Mongo indexes"
    $scriptPath = Join-Path $logDir 'repair-mongo-indexes.js'
    $script = @'
var database = db.getSiblingDB('yygh_hosp');
var repairs = [
  { collection: 'Hospital', expectedName: 'hoscode', oldName: 'hoscode_1', key: { hoscode: 1 }, unique: true },
  { collection: 'Department', expectedName: 'depcode', oldName: 'depcode_1', key: { depcode: 1 }, unique: true }
];

function sameKey(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

repairs.forEach(function (repair) {
  var collection = database.getCollection(repair.collection);
  var indexes = collection.getIndexes();
  indexes.forEach(function (index) {
    if (index.name === repair.oldName && sameKey(index.key, repair.key) && !!index.unique === !!repair.unique) {
      print('Drop legacy index ' + repair.collection + '.' + repair.oldName);
      collection.dropIndex(repair.oldName);
    }
  });

  var hasExpected = false;
  collection.getIndexes().forEach(function (index) {
    if (index.name === repair.expectedName && sameKey(index.key, repair.key) && !!index.unique === !!repair.unique) {
      hasExpected = true;
    }
  });
  if (!hasExpected) {
    print('Create expected index ' + repair.collection + '.' + repair.expectedName);
    collection.createIndex(repair.key, { name: repair.expectedName, unique: repair.unique });
  }
});
'@
    Set-Content -LiteralPath $scriptPath -Value $script -Encoding UTF8
    & $paths.MongoShell --quiet $scriptPath
}

function Start-ProjectServices {
    Write-Step "Start project services"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $managerScript start

    Write-Step "Wait project ports"
    Write-Warn "First startup can take several minutes because Java services and the Nuxt frontend compile concurrently."
    $allReady = $true
    foreach ($item in @(
        @{Name='service-hosp'; Port=8201},
        @{Name='service-cmn'; Port=8202},
        @{Name='service-user'; Port=8150},
        @{Name='service-msm'; Port=8204},
        @{Name='service-oss'; Port=8205},
        @{Name='service-order'; Port=8206},
        @{Name='service-task'; Port=8207},
        @{Name='service-statistics'; Port=8208},
        @{Name='service-agent'; Port=8210},
        @{Name='service-gateway'; Port=80},
        @{Name='yygh-site'; Port=3000}
    )) {
        if (-not (Wait-ServicePort $item.Name $item.Port 300)) {
            $allReady = $false
            break
        }
    }

    if (-not $allReady) {
        throw 'Project services did not become ready. See the log tail above and runtime-logs for details.'
    }
}

function Stop-ProjectServices {
    Write-Step "Stop project services"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $managerScript stop
}

function Show-ProjectStatus {
    Write-Step "Project service status"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $managerScript status
}

switch ($Action) {
    'start' {
        Start-Middleware
        Show-MiddlewareStatus
        Repair-MongoIndexes
        Start-ProjectServices
        Show-ProjectStatus
        Write-Ok 'Open http://localhost:3000'
    }
    'restart' {
        Stop-ProjectServices
        Start-Sleep -Seconds 2
        Start-Middleware
        Show-MiddlewareStatus
        Repair-MongoIndexes
        Start-ProjectServices
        Show-ProjectStatus
        Write-Ok 'Open http://localhost:3000'
    }
    'status' {
        Show-MiddlewareStatus
        Show-ProjectStatus
    }
    'stop' {
        Stop-ProjectServices
        Write-Ok 'Project services stopped. Middleware is left running.'
    }
}
