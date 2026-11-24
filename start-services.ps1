param([switch]$Build,[switch]$CheckOnly)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$backend=Join-Path $root 'girl-shop/backend'
function Listening([int]$port){$socket=New-Object Net.Sockets.TcpClient;try{$task=$socket.ConnectAsync('127.0.0.1',$port);return $task.Wait(1000)-and $socket.Connected}catch{return $false}finally{$socket.Dispose()}}
if($CheckOnly){foreach($port in @(3306,6379,5672,8081,8090)){[pscustomobject]@{Port=$port;Listening=(Listening $port)}};exit}
if(-not(Listening 3306)){throw 'MySQL 3306 is not running. Start the existing MySQL service; no database will be recreated.'}
if(-not(Listening 6379)){Start-Process 'D:/redis/Redis-6.2.24-Windows-x64-msys2-with-Service/redis-server.exe' -ArgumentList 'redis.conf' -WorkingDirectory 'D:/redis/Redis-6.2.24-Windows-x64-msys2-with-Service' -WindowStyle Hidden}
if(-not(Listening 5672)){
 $settings=Get-Content 'C:/Users/18835/AppData/Roaming/Docker/settings-store.json' -Raw|ConvertFrom-Json
 if($settings.CustomWslDistroDir -ne 'D:\DockerData'){throw 'Docker is not configured to use D:\DockerData. Refusing to start an empty replacement.'}
 Start-Process 'C:/Users/18835/AppData/Local/Programs/DockerDesktop/Docker Desktop.exe' -WindowStyle Hidden
 Write-Host 'Waiting for Docker. No resets or data migration will be performed.'
 $ready=$false
 for($i=0;$i -lt 20;$i++){ $probe=Start-Process 'docker' -ArgumentList 'info' -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $backend '.runtime/docker-probe.log') -RedirectStandardError (Join-Path $backend '.runtime/docker-probe-error.log');if($probe.WaitForExit(2000)-and $probe.ExitCode -eq 0){$ready=$true;break};if(-not $probe.HasExited){$probe.Kill()};Start-Sleep -Seconds 1}
 if(-not $ready){throw 'Docker did not become ready. Inspect Docker Desktop; do not reset D drive data.'}
 docker start rabbitmq
 if($LASTEXITCODE -ne 0){throw 'Existing rabbitmq container could not start.'}
}
if(-not(Listening 8090)){$nginx=Join-Path $root 'nginx-1.18.0/nginx-1.18.0';Start-Process (Join-Path $nginx 'nginx.exe') -WorkingDirectory $nginx -WindowStyle Hidden}
if(Listening 8081){Write-Host '8081 is already running. Keeping the current backend (including IDEA instances).'}else{
 if($Build){Push-Location $backend;try{$env:JAVA_HOME='D:/jdk-17.0.2';mvn -q -DskipTests package;if($LASTEXITCODE -ne 0){throw 'Build failed'}}finally{Pop-Location}}
 if(-not(Test-Path (Join-Path $backend 'target/girl-shop-1.0.0.jar'))){throw 'Missing JAR; rerun with -Build.'}
 Start-Process 'D:/jdk-17.0.2/bin/java.exe' -ArgumentList '-Xms128m','-Xmx512m','-XX:MaxDirectMemorySize=128m','-jar','target/girl-shop-1.0.0.jar' -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $backend '.runtime/service-stdout.log') -RedirectStandardError (Join-Path $backend '.runtime/service-stderr.log')
}
Write-Host 'Startup requested. Open http://localhost:8090 . Run this script -CheckOnly to verify ports.'
