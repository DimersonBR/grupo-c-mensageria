param([switch]$Receber, [switch]$Confirmar, [switch]$Pedidos, [switch]$DemoPedidos, [switch]$ListarPedidos, [switch]$Testar)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $localJdk = Get-ChildItem -Path "$PSScriptRoot/.tools/jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    $maven = "$PSScriptRoot/.tools/apache-maven-3.9.11/bin/mvn.cmd"
    if (-not (Test-Path -LiteralPath $maven)) { $maven = 'mvn' }
    $appArgs = @()
    if ($Receber) { $appArgs += '--receber' }
    if ($Confirmar) { $appArgs += '--confirmar' }
    if ($Pedidos) { $appArgs += '--pedidos' }
    if ($DemoPedidos) { $appArgs += '--demo-pedidos' }
    if ($ListarPedidos) { $appArgs += '--listar-pedidos' }
    if ($Testar) {
        & $maven '-q' "-Dmaven.repo.local=$PSScriptRoot/.tools/m2" 'test'
        if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
        return
    }
    & $maven '-q' "-Dmaven.repo.local=$PSScriptRoot/.tools/m2" 'compile' 'exec:java' "-Dexec.args=$($appArgs -join ' ')"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao executar o programa Java.' }
} finally {
    Pop-Location
}
