# Pub/Sub em Java — Grupo C

## Trabalho — Questão 2: API de pedidos

Na pasta do projeto, execute:

```powershell
.\executar.ps1 -Api
```

Em outro PowerShell, na mesma pasta, execute `.\executar.ps1 -Pedidos` para receber pedidos enquanto a API funciona. Antes de usar esta versão pela primeira vez, encerre as execuções antigas com Ctrl+C e reinicie ambas.

Abra `http://127.0.0.1:8080/orders`. Consulte [rotas, filtros e exemplos](docs/questao-2.md).

A API consulta o mesmo banco local da questão 1 e não precisa da credencial Google. No clone do GitHub, instale JDK 17+ e Maven; as ferramentas e o banco da pasta original não são publicados.

## Trabalho — Questão 1

O consumidor de pedidos com persistência relacional está implementado. Consulte [instruções e DER](docs/questao-1.md).

```powershell
.\executar.ps1 -DemoPedidos
.\executar.ps1 -ListarPedidos
.\executar.ps1 -Pedidos
```

`-Pedidos` salva os pedidos e confirma automaticamente apenas após a gravação. Os comandos antigos abaixo continuam destinados à inspeção de mensagens.

Projeto: `serjava-demo`  
Assinatura: `projects/serjava-demo/subscriptions/grupo-c`  
Tópico informado: `projects/serjava-demo/topics/aula-pub`

Projeto Maven com a biblioteca oficial `google-cloud-pubsub`. O JDK 21 e o Maven foram preparados em `.tools`. Execute os comandos no PowerShell, nesta pasta.

Use `executar.cmd`: ele chama o Maven diretamente e não exige habilitar scripts PowerShell.

Código principal: `src/main/java/br/edu/grupoc/SubscriberApp.java`.

## Testar acesso sem ler mensagens

```powershell
.\executar.cmd
```

## Receber até 10 mensagens sem confirmar

```powershell
.\executar.cmd -Receber
```

Exibe um lote e libera as mensagens para nova entrega. Cada chamada aguarda até 25 segundos. Um resultado vazio não garante que toda a fila esteja vazia.

## Receber e confirmar

```powershell
.\executar.cmd -Receber -Confirmar
```

Confirma as mensagens após exibi-las; elas deixam de aguardar entrega nesta assinatura. Use quando quiser concluir o consumo.

## Recriar o ambiente em outro computador

Com JDK 17 ou superior e Maven instalados, configure `JAVA_HOME` para o JDK e execute na raiz do projeto:

```powershell
mvn compile exec:java
mvn compile exec:java "-Dexec.args=--receber"
```

Mantenha `sa-grupo-c-key.json` nesta pasta. O programa lê a credencial diretamente, sem exigir gcloud ou variável de ambiente. A chave está excluída pelo `.gitignore`; não a publique.

Na IDE (IntelliJ, Eclipse ou VS Code), importe `pom.xml` como projeto Maven, selecione JDK 17+ e execute `br.edu.grupoc.SubscriberApp` com o diretório de trabalho na raiz do projeto. Sem argumentos, apenas testa o acesso; `--receber` consulta mensagens e `--receber --confirmar` também confirma o consumo.

Referência: [teste de permissões do Google Pub/Sub](https://cloud.google.com/pubsub/docs/samples/pubsub-test-subscription-permissions?hl=pt-BR).
