param(
    [switch]$Receber,
    [switch]$Confirmar,
    [switch]$Pedidos,
    [switch]$DemoPedidos,
    [switch]$ListarPedidos,
    [switch]$Semear,
    [int]$Quantidade = 50,
    [long]$Semente = 1,
    [switch]$Testar,
    [switch]$Api,
    [switch]$Ajuda
)
$ErrorActionPreference = 'Stop'

function Get-JdkMajor([string]$jdkPath) {
    if (-not $jdkPath) { return 0 }
    if (-not (Test-Path -LiteralPath (Join-Path $jdkPath 'bin\javac.exe'))) { return 0 }
    $release = Join-Path $jdkPath 'release'
    if (-not (Test-Path -LiteralPath $release)) { return 0 }
    $match = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
    if (-not $match) { return 0 }
    $parts = $match.Matches[0].Groups[1].Value -split '[._\-+]'
    $major = if ($parts[0] -eq '1') { $parts[1] } else { $parts[0] }
    $number = 0
    if ([int]::TryParse($major, [ref]$number)) { return $number }
    return 0
}

function Find-Jdk {
    $candidates = New-Object System.Collections.Generic.List[string]
    Get-ChildItem -Path "$PSScriptRoot\.tools\jdk" -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { $candidates.Add($_.FullName) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    $roots = @(
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu',
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )
    foreach ($root in $roots) {
        Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk*' } |
            ForEach-Object { $candidates.Add($_.FullName) }
    }
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { $candidates.Add((Split-Path -Parent (Split-Path -Parent $javac.Source))) }

    $melhor = $null
    $melhorVersao = 0
    foreach ($candidate in $candidates) {
        $versao = Get-JdkMajor $candidate
        if ($versao -ge 17 -and $versao -gt $melhorVersao) { $melhor = $candidate; $melhorVersao = $versao }
    }
    if (-not $melhor) {
        throw @'
Nenhum JDK 17 ou superior foi encontrado.
Um JRE 8 na variavel PATH causa o erro "class file version 61.0 ... up to 52.0".
Instale o JDK 17+ (https://adoptium.net) ou aponte JAVA_HOME para a pasta do JDK e execute novamente.
'@
    }
    return @{ Path = $melhor; Version = $melhorVersao }
}

function Find-Maven {
    $local = Get-ChildItem -Path "$PSScriptRoot\.tools" -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($local -and (Test-Path -LiteralPath (Join-Path $local.FullName 'bin\mvn.cmd'))) {
        return (Join-Path $local.FullName 'bin\mvn.cmd')
    }
    $wrapper = Join-Path $PSScriptRoot 'mvnw.cmd'
    if (Test-Path -LiteralPath $wrapper) { return $wrapper }
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mvn) { return $mvn.Source }
    throw 'Maven nao encontrado. Use o mvnw.cmd do repositorio ou instale o Maven 3.9+.'
}

if ($Ajuda) {
    @'
Uso: .\executar.ps1 [opcao]

  -Semear [-Quantidade 50] [-Semente 1]  Popula o banco local com pedidos ficticios
  -ListarPedidos                         Lista os pedidos gravados no banco
  -Api                                   Sobe a API REST em http://127.0.0.1:8080/orders
  -DemoPedidos                           Grava o pedido de exemplo do repositorio
  -Pedidos                               Consome a assinatura real do Pub/Sub (exige credencial)
  -Receber [-Confirmar]                  Le mensagens da assinatura sem persistir (exige credencial)
  -Testar                                Executa os testes automatizados
'@
    return
}

Push-Location $PSScriptRoot
try {
    $jdk = Find-Jdk
    $env:JAVA_HOME = $jdk.Path
    $env:PATH = "$($jdk.Path)\bin;$env:PATH"
    Write-Host "JDK $($jdk.Version): $($jdk.Path)"

    $maven = Find-Maven
    $mavenArgs = @('-B')
    if (Test-Path -LiteralPath "$PSScriptRoot\.tools\m2") { $mavenArgs += "-Dmaven.repo.local=$PSScriptRoot\.tools\m2" }

    if ($Testar) {
        & $maven @mavenArgs 'test'
        if ($LASTEXITCODE -ne 0) { Write-Host 'Testes falharam.' -ForegroundColor Red }
        exit $LASTEXITCODE
    }

    $appArgs = @()
    if ($Api) { $appArgs += '--api' }
    if ($Receber) { $appArgs += '--receber' }
    if ($Confirmar) { $appArgs += '--confirmar' }
    if ($Pedidos) { $appArgs += '--pedidos' }
    if ($DemoPedidos) { $appArgs += '--demo-pedidos' }
    if ($ListarPedidos) { $appArgs += '--listar-pedidos' }
    if ($Semear) { $appArgs += @('--semear', $Quantidade, $Semente) }

    & $maven @mavenArgs '-q' 'compile' 'exec:java' "-Dexec.args=$($appArgs -join ' ')"
    if ($LASTEXITCODE -ne 0) { Write-Host 'O programa Java terminou com erro. Veja a mensagem acima.' -ForegroundColor Red }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
