# Pub/Sub em Java — Grupo C

Consumidor de pedidos (questão 1) e API REST de consulta (questão 2), em Java 17 com H2 e Google Cloud Pub/Sub.

## Começar agora

Requisito único: **JDK 17 ou superior**. O Maven vem no repositório pelo wrapper (`mvnw.cmd`) e é baixado na primeira execução.

```powershell
.\executar.ps1 -Semear          # popula o banco local com pedidos ficticios
.\executar.ps1 -ListarPedidos   # confere os pedidos gravados
.\executar.ps1 -Api             # sobe a API em http://127.0.0.1:8080/orders
.\executar.ps1 -Testar          # roda os testes
```

Se o PowerShell bloquear scripts, use `.\executar.cmd` com as mesmas opções. Guia completo, lista de comandos e solução dos erros mais comuns: [docs/como-rodar.md](docs/como-rodar.md).

Nada disso precisa de conta Google: os dados fictícios passam pelas mesmas validações e transações do consumidor real. A credencial (`sa-grupo-c-key.json`) só é necessária para `-Pedidos` e `-Receber`, que acessam a assinatura de verdade, e não é publicada no repositório.

## Trabalho — Questão 2: API de pedidos

```powershell
.\executar.ps1 -Api
```

Em outro PowerShell, na mesma pasta, execute `.\executar.ps1 -Pedidos` (ou `-Semear`) para gravar pedidos enquanto a API funciona. Abra `http://127.0.0.1:8080/orders`. Consulte [rotas, filtros e exemplos](docs/questao-2.md).

## Trabalho — Questão 1

O consumidor de pedidos com persistência relacional está implementado. Consulte [instruções e DER](docs/questao-1.md).

```powershell
.\executar.ps1 -Semear
.\executar.ps1 -DemoPedidos
.\executar.ps1 -ListarPedidos
.\executar.ps1 -Pedidos
```

`-Pedidos` salva os pedidos e confirma automaticamente apenas após a gravação.

Projeto: `serjava-demo`
Assinatura: `projects/serjava-demo/subscriptions/grupo-c`
Tópico informado: `projects/serjava-demo/topics/aula-pub`

Código principal: `src/main/java/br/edu/grupoc/SubscriberApp.java`.

## Inspecionar a assinatura sem persistir

```powershell
.\executar.ps1                       # testa o acesso, sem ler mensagens
.\executar.ps1 -Receber              # exibe ate 10 mensagens e as devolve a fila
.\executar.ps1 -Receber -Confirmar   # exibe e confirma o consumo
```

Cada chamada aguarda até 25 segundos. Um resultado vazio não garante que a fila esteja vazia. Esses comandos exigem `sa-grupo-c-key.json` na raiz do projeto; o arquivo está no `.gitignore` e não deve ser publicado.

## Executar sem os scripts

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=--semear 50"
.\mvnw.cmd compile exec:java "-Dexec.args=--api"
.\mvnw.cmd test
```

Na IDE (IntelliJ, Eclipse ou VS Code), importe `pom.xml` como projeto Maven, selecione JDK 17+ e execute `br.edu.grupoc.SubscriberApp` com o diretório de trabalho na raiz do projeto.

Referência: [teste de permissões do Google Pub/Sub](https://cloud.google.com/pubsub/docs/samples/pubsub-test-subscription-permissions?hl=pt-BR).
