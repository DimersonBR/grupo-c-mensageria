# Como rodar o projeto

Guia único de execução no Windows. Vale para quem acabou de clonar o repositório.

## 1. Pré-requisitos

| Item | Situação |
|---|---|
| JDK 17 ou superior | **Obrigatório instalar.** Baixe em [adoptium.net](https://adoptium.net) (Temurin 17 ou 21) |
| Maven | **Não precisa instalar.** O repositório traz o Maven Wrapper (`mvnw.cmd`), que baixa o Maven 3.9.11 na primeira execução |
| Conta Google / credencial | Só é necessária para consumir a fila real do Pub/Sub (`-Pedidos` e `-Receber`). Todo o resto roda com dados fictícios |
| Banco de dados | Não precisa instalar nada. O H2 grava em arquivo, em `data/pedidos.mv.db` |

Na primeira execução o Maven baixa o Maven e as dependências (uns 200 MB, alguns minutos). Depois disso os comandos levam segundos.

Não é preciso configurar `JAVA_HOME`: o `executar.ps1` procura um JDK 17+ sozinho (em `.tools/jdk`, no `JAVA_HOME`, em `C:\Program Files\Java`, Adoptium, Microsoft, Corretto e Zulu) e ignora JREs antigos que estejam no `PATH`.

## 2. Rodar em três comandos

Abra o PowerShell na pasta do projeto:

```powershell
git clone https://github.com/DimersonBR/grupo-c-mensageria
cd grupo-c-mensageria

.\executar.ps1 -Semear          # cria 50 pedidos fictícios no banco local
.\executar.ps1 -ListarPedidos   # confere o que foi gravado
.\executar.ps1 -Api             # sobe a API em http://127.0.0.1:8080/orders
```

Com a API no ar, abra **outro** PowerShell para consultar:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/orders?page=1&size=5' | ConvertTo-Json -Depth 20
Invoke-RestMethod 'http://127.0.0.1:8080/orders/financial-summary' | ConvertTo-Json -Depth 20
```

Ou abra `http://127.0.0.1:8080/orders` no navegador. Use Ctrl+C para parar a API.

Se o PowerShell bloquear scripts (`execução de scripts foi desabilitada neste sistema`), use o atalho equivalente, que não depende da política de execução:

```powershell
.\executar.cmd -Semear
```

## 3. Comandos disponíveis

| Comando | O que faz |
|---|---|
| `.\executar.ps1 -Ajuda` | Lista as opções |
| `.\executar.ps1 -Semear` | Gera 50 pedidos fictícios (clientes, produtos, vendedores, status e formas de pagamento variados) |
| `.\executar.ps1 -Semear -Quantidade 300` | Gera a quantidade informada (1 a 5000) |
| `.\executar.ps1 -Semear -Quantidade 300 -Semente 7` | Outra semente gera outro conjunto de pedidos |
| `.\executar.ps1 -ListarPedidos` | Lista pedidos, itens e totais gravados |
| `.\executar.ps1 -DemoPedidos` | Grava o pedido de exemplo de `src/main/resources/pedido-exemplo.json` |
| `.\executar.ps1 -Api` | Sobe a API REST de leitura |
| `.\executar.ps1 -Testar` | Roda os 14 testes automatizados |
| `.\executar.ps1 -Pedidos` | Consome a assinatura real do Pub/Sub e grava os pedidos (exige credencial) |
| `.\executar.ps1 -Receber [-Confirmar]` | Lê mensagens da assinatura sem persistir (exige credencial) |

A geração é idempotente: rodar `-Semear` duas vezes com a mesma quantidade e semente não duplica pedidos, porque o UUID é o mesmo. Isso serve de demonstração da regra de deduplicação exigida na questão 1.

## 4. Erros comuns

**`class file version 61.0 ... only recognizes class file versions up to 52.0`**
O Maven estava rodando sobre um JRE 8 (normalmente o `C:\ProgramData\Oracle\Java\javapath` no `PATH`). O `executar.ps1` atual escolhe o JDK 17+ automaticamente e resolve isso. Se aparecer de novo, apague a pasta `target` e rode outra vez.

**`Nenhum JDK 17 ou superior foi encontrado`**
Só há JRE instalado. Instale o JDK (Temurin 17 ou 21) e reabra o PowerShell.

**`mvn não é reconhecido`**
Não usamos mais o `mvn` do sistema; rode pelos scripts `executar.ps1`/`executar.cmd`, ou diretamente `.\mvnw.cmd compile exec:java "-Dexec.args=--semear 50"`.

**`Credencial nao encontrada em ...sa-grupo-c-key.json`**
Esperado num clone: o arquivo está no `.gitignore` e não é publicado. Use `-Semear` para trabalhar com dados fictícios, ou peça a chave a quem a possui e coloque-a na raiz do projeto.

**`a porta 8080 esta ocupada`**
Outra API já está rodando. Feche-a com Ctrl+C ou escolha outra porta antes de subir:

```powershell
$env:ORDERS_API_PORT = '8081'
.\executar.ps1 -Api
```

**Erro de banco ao rodar dois comandos ao mesmo tempo**
O H2 usa `AUTO_SERVER=TRUE`, então API e consumidor podem compartilhar o arquivo. Se tiver definido `ORDERS_DB_URL`, use exatamente a mesma URL nos dois terminais, incluindo `;AUTO_SERVER=TRUE;WRITE_DELAY=0`.

## 5. Variáveis de ambiente

| Variável | Padrão | Uso |
|---|---|---|
| `ORDERS_DB_URL` | `jdbc:h2:file:./data/pedidos;AUTO_SERVER=TRUE;WRITE_DELAY=0` | Aponta para outro banco H2 |
| `ORDERS_API_PORT` | `8080` | Porta da API |
| `ORDERS_SUBSCRIPTION` | `projects/serjava-demo/subscriptions/grupo-c` | Assinatura do Pub/Sub |
| `ORDERS_CREDENTIAL` | `sa-grupo-c-key.json` | Caminho da chave da conta de serviço |

## 6. Rodar pela IDE

Importe o `pom.xml` como projeto Maven, selecione um JDK 17+, defina o diretório de trabalho na raiz do projeto e execute `br.edu.grupoc.SubscriberApp` com um destes argumentos: `--semear 50`, `--listar-pedidos`, `--api`, `--demo-pedidos` ou `--pedidos`.

## 7. Dados fictícios

`-Semear` grava pedidos no mesmo formato do contrato do trabalho, passando pelas mesmas validações e transações do consumidor real (`OrderRepository.save`). Os dados vêm de `src/main/java/br/edu/grupoc/SeedOrders.java`: 6 clientes, 8 produtos em 4 categorias, 3 vendedores, os 5 status, 3 formas de pagamento e datas distribuídas nos últimos 90 dias. Para mudar o catálogo, edite as listas no topo dessa classe.
