# Questão 2 — API REST de pedidos em Java

Implementação com o servidor HTTP do JDK, Gson e JDBC/H2. As respostas usam JSON UTF-8. A API fica acessível apenas neste computador, em `127.0.0.1:8080`.

## Iniciar

Na pasta do projeto, execute:

```powershell
.\executar.ps1 -Api
```

Para consultar com dados, rode antes `.\executar.ps1 -Semear`, que popula o banco local com pedidos fictícios sem exigir credencial do Google. Requisitos e erros comuns estão em [como rodar o projeto](como-rodar.md).

O H2 usa `AUTO_SERVER=TRUE`, permitindo acesso ao mesmo arquivo por processos separados. Em outro PowerShell, na mesma pasta, execute `.\executar.ps1 -Pedidos`: os pedidos gravados ficam disponíveis nas próximas consultas da API. A API mantém uma conexão aberta durante a execução para manter estável o servidor automático do H2.

Na primeira execução desta atualização, encerre as versões antigas da API e do consumidor com Ctrl+C. Depois inicie a API e o consumidor atualizados. O esquema é atualizado automaticamente: vendedor e forma de pagamento são extraídos dos pedidos antigos, sem substituir o payload nem o horário de indexação.

Se você usa `ORDERS_DB_URL`, utilize exatamente a mesma URL nos dois terminais e inclua `;AUTO_SERVER=TRUE;WRITE_DELAY=0` para bancos em arquivo. Sem essa variável, a configuração compartilhada já é o padrão. Não use `AUTO_SERVER` em bancos em memória.

Em outro PowerShell:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/orders?page=1&size=10&sort=created_at,desc' | ConvertTo-Json -Depth 20
Invoke-RestMethod 'http://127.0.0.1:8080/orders/financial-summary' | ConvertTo-Json -Depth 20
```

Para outra porta, defina `$env:ORDERS_API_PORT = '8081'` antes de iniciar. `ORDERS_DB_URL` permite selecionar outro banco H2. Não são necessárias credenciais do Google para consultar dados locais.

Sem os scripts, com JDK 17+ instalado:

```powershell
.\mvnw.cmd compile exec:java "-Dexec.args=--api"
```

O banco original permanece em `Projeto 1/data/pedidos.mv.db`. Executar no clone usa outro banco, inicialmente vazio. Para ler o original pelo clone, defina `ORDERS_DB_URL` com a URL JDBC absoluta do banco original, sem o sufixo `.mv.db`.

## Contrato das rotas

| Método e rota | Resultado |
|---|---|
| `GET /orders` | `{"data":[...pedidos...],"pagination":{"page":1,"size":20,"total_elements":201,"total_pages":11}}` |
| `GET /orders/{uuid}` | Um pedido completo no formato do payload do trabalho |
| `GET /orders/{uuid}/items` | `{"items":[...]}`, preservando a estrutura dos itens do payload |
| `GET /orders/financial-summary` | Resumo financeiro de todos os pedidos correspondentes aos filtros, sem paginação |

Os números do exemplo de paginação são ilustrativos. Cliente, seller, produtos, categoria, entrega, pagamento e metadata são preservados conforme o JSON recebido. O timestamp interno de indexação fica no banco e não é acrescentado ao contrato de resposta.

## Paginação e filtros de /orders

| Parâmetro | Regra |
|---|---|
| `page` | Inteiro a partir de 1; padrão 1 |
| `size` | Inteiro entre 1 e 100; padrão 20 |
| `sort` | `created_at,asc` ou `created_at,desc`; padrão decrescente |
| `customer.id` | Igualdade exata com o ID do cliente |
| `product.id` | Pedidos que contenham o produto; retorna o pedido com todos os seus itens |
| `seller.id` | Igualdade exata com o ID do vendedor |
| `status` | Igualdade exata com o status recebido |

Filtros combinam-se por AND. Datas iguais são desempatas pelo UUID crescente, garantindo paginação determinística. Página além do último resultado retorna lista vazia e mantém os metadados.

Exemplo: `/orders?customer.id=7788&seller.id=55&status=created&page=1&size=10`.

## Resumo financeiro

Aceita `seller.id`, `start_date` e `end_date`. As datas filtram `created_at`, e não a data de indexação. Limites são inclusivos. Uma data `YYYY-MM-DD` representa o dia inteiro em UTC; também são aceitos timestamps ISO 8601 com fuso. Em URLs, codifique o sinal `+` do fuso como `%2B`.

Exemplo: `/orders/financial-summary?seller.id=55&start_date=2025-10-01&end_date=2025-10-31`.

```json
{
  "total_orders": 1,
  "total_revenue": 5000.00,
  "average_order_value": 5000.00,
  "by_status": {"created": 1, "paid": 0, "shipped": 0, "delivered": 0, "canceled": 0},
  "by_payment_method": {"pix": {"count": 1, "total": 5000.00}}
}
```

O PDF apresenta status diferentes no exemplo e nas considerações. São usados os status efetivamente recebidos, com as cinco categorias das considerações inicialmente zeradas. Outros status recebidos também aparecem no resumo. Formas de pagamento sem ocorrência são omitidas; pagamento sem método é agrupado como `unknown`.

Sem resultados, contagem, total e média são zero. Como o enunciado não define exclusões, `total_revenue` soma todos os pedidos filtrados, inclusive cancelados, e não representa apenas pagamentos liquidados.

## Cálculos e erros

Os totais do JSON recebido são ignorados. Cada resposta calcula `item.total = unit_price × quantity` a partir da tabela `item_pedido`, e `order.total` é a soma dos itens. Valores usam `BigDecimal`; a média é arredondada para duas casas decimais com HALF_UP.

- `200`: consulta válida, inclusive resultados vazios.
- `400`: paginação, ordenação, datas ou parâmetros inválidos; parâmetros desconhecidos, vazios ou repetidos são rejeitados.
- `404`: pedido ou rota inexistente.
- `405`: método diferente de GET, com cabeçalho `Allow: GET`.
- `500`: falha interna de consulta, sem expor dados do banco.

Os erros usam `{"error":"codigo","message":"descricao"}`.

## Testes e limites

Execute `.\executar.ps1 -Testar`. Os testes HTTP usam banco temporário e verificam as quatro rotas, paginação, ordenação, filtros combinados, datas, totais calculados, resumo vazio e erros. Os testes anteriores de persistência permanecem ativos.

Filtros, ordenação, contagem, paginação e agrupamentos financeiros são executados no SQL. Apenas os payloads da página selecionada são carregados; a consulta por UUID usa a chave primária. Filtros usam parâmetros preparados, e o filtro de produto usa EXISTS para não duplicar pedidos. Índices apoiam consultas por cliente, vendedor, status, produto e data. Contagem e dados de uma página são lidos na mesma transação com isolamento SERIALIZABLE.

O resumo agrega valores no banco, sem desserializar todos os payloads. Cada pedido conta apenas uma vez, mesmo com vários itens. Os totais continuam sendo calculados dinamicamente. A paginação usa LIMIT/OFFSET; páginas muito profundas ainda podem exigir mais trabalho do banco.

Os testes também verificam a migração de um banco antigo, sua reexecução sem alterar dados, o acesso por dois processos Java e a leitura HTTP de pedidos inseridos pelo segundo processo. Nenhum desses testes usa os pedidos reais ou acessa o Pub/Sub.

Referência técnica: [servidor HTTP do Java](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html).
