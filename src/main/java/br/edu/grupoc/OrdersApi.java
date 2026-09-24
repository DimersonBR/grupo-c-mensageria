package br.edu.grupoc;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;


import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** API local de leitura. Cada requisicao consulta uma visao consistente do banco. */
public final class OrdersApi implements AutoCloseable {
    private final String databaseUrl;
    private final HttpServer server;
    // Um conjunto limitado de threads impede a criacao ilimitada de workers sob carga.
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    public OrdersApi(String databaseUrl, int port) throws IOException {
        this.databaseUrl = databaseUrl;
        // A API escuta somente na maquina local; porta zero e util para testes.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(1); executor.shutdownNow(); }

    public static void run() throws Exception {
        String url = Database.url();
        // Atualiza o esquema e mantem a conexao proprietaria do AUTO_SERVER aberta.
        try (var repository = new OrderRepository(url)) {
            int port = Integer.parseInt(System.getenv().getOrDefault("ORDERS_API_PORT", "8080"));
            var api = new OrdersApi(url, port);
            // Garante que servidor e executor sejam encerrados ao receber Ctrl+C.
            Runtime.getRuntime().addShutdownHook(new Thread(api::close));
            api.start();
            System.out.println("API disponivel em http://127.0.0.1:" + api.port() + "/orders. Ctrl+C para parar.");
            new java.util.concurrent.CountDownLatch(1).await();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            // Todas as rotas desta API sao somente de leitura.
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, error("method_not_allowed", "Use GET.")); return;
            }
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
            if (path.equals("/orders") || path.equals("/orders/financial-summary")) {
                boolean summary = path.endsWith("financial-summary");
                // Cada rota possui sua propria lista branca de parametros.
                Set<String> allowed = summary
                        ? Set.of("seller.id", "start_date", "end_date")
                        : Set.of("customer.id", "product.id", "seller.id", "status", "start_date", "end_date",
                                "page", "size", "sort");
                if (!allowed.containsAll(query.keySet())) throw new IllegalArgumentException("Parametro desconhecido para esta rota.");
                Instant start = boundary(query.get("start_date"), false);
                Instant end = boundary(query.get("end_date"), true);
                if (start != null && end != null && start.isAfter(end))
                    throw new IllegalArgumentException("start_date deve ser anterior ou igual a end_date.");
                int page = positive(query, "page", 1, Integer.MAX_VALUE);
                int size = positive(query, "size", 20, 100);
                String sort = query.getOrDefault("sort", "created_at,desc");
                if (!Set.of("created_at,asc", "created_at,desc").contains(sort))
                    throw new IllegalArgumentException("sort deve ser created_at,asc ou created_at,desc.");
                var queries = new OrderQueries(databaseUrl);
                send(exchange, 200, summary
                        ? queries.summary(query, start, end)
                        : queries.page(query, page, size, sort, start, end));
                return;
            }
            String[] parts = path.split("/", -1);
            // Reconhece /orders/{uuid} e /orders/{uuid}/items sem um framework externo.
            if ((parts.length == 3 || (parts.length == 4 && parts[3].equals("items")))
                    && parts[1].equals("orders") && !parts[2].isBlank()) {
                if (!query.isEmpty()) throw new IllegalArgumentException("Esta rota nao aceita parametros.");
                Optional<JsonObject> order = new OrderQueries(databaseUrl).detail(parts[2]);
                if (order.isEmpty()) { send(exchange, 404, error("not_found", "Pedido nao encontrado.")); return; }
                if (parts.length == 4) {
                    JsonObject items = new JsonObject(); items.add("items", order.get().get("items"));
                    send(exchange, 200, items);
                } else send(exchange, 200, order.get());
                return;
            }
            send(exchange, 404, error("not_found", "Rota nao encontrada."));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, error("invalid_request", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Falha na consulta da API: " + e.getClass().getSimpleName());
            send(exchange, 500, error("internal_error", "Nao foi possivel consultar o banco."));
        } finally { exchange.close(); }
    }

    private static Instant boundary(String value, boolean end) {
        if (value == null) return null;
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                Instant day = LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
                // Uma data final sem horario inclui ate o ultimo nanossegundo daquele dia.
                return end ? day.plus(Duration.ofDays(1)).minusNanos(1) : day;
            }
            return OffsetDateTime.parse(value).toInstant();
        } catch (java.time.DateTimeException e) { throw new IllegalArgumentException("Data invalida: use YYYY-MM-DD ou ISO 8601 com fuso."); }
    }

    private static int positive(Map<String, String> q, String key, int fallback, int maximum) {
        try {
            int value = Integer.parseInt(q.getOrDefault(key, Integer.toString(fallback)));
            if (value < 1 || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " deve estar entre 1 e " + maximum + "."); }
    }

    private static Map<String, String> parameters(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isEmpty()) return result;
        // putIfAbsent tambem detecta chaves repetidas, evitando interpretacoes ambiguas.
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (value.isBlank() || result.putIfAbsent(key, value) != null)
                throw new IllegalArgumentException("Parametros vazios ou repetidos nao sao permitidos.");
        }
        return result;
    }
    private static JsonObject error(String code, String message) {
        JsonObject error = new JsonObject(); error.addProperty("error", code); error.addProperty("message", message); return error;
    }
    private static void send(HttpExchange exchange, int status, JsonElement body) throws IOException {
        byte[] bytes = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        // Respostas de consulta nao ficam armazenadas pelo navegador ou por proxies locais.
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

