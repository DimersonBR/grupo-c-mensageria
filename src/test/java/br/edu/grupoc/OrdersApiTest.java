package br.edu.grupoc;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class OrdersApiTest {
    @TempDir Path directory;
    OrdersApi api;
    HttpClient http = HttpClient.newHttpClient();
    String url;

    @BeforeEach void setup() throws Exception {
        url = "jdbc:h2:file:" + directory.resolve("api").toAbsolutePath();
        try (var repository = new OrderRepository(url)) {
            repository.save(order("A", "2026-09-01T10:00:00Z", "paid", 1, 55, "pix", "10.25", 2));
            repository.save(order("B", "2026-09-02T10:00:00Z", "canceled", 2, 66, "credit_card", "3.10", 3));
            repository.save(order("C", "2026-09-02T10:00:00Z", "paid", 1, 55, "pix", "1.00", 1));
        }
        api = new OrdersApi(url, 0); api.start();
    }
    @AfterEach void stop() { if (api != null) api.close(); }
    String order(String id, String date, String status, int customer, int seller, String method, String price, int quantity) {
        return """
          {"uuid":"%s","created_at":"%s","channel":"mobile_app","status":"%s","total":999,
          "customer":{"id":%d,"name":"Cliente","email":"cliente@example.com","document":"123"},
          "seller":{"id":%d,"name":"Loja","city":"São Paulo","state":"SP"},
          "items":[{"id":1,"product":{"id":"produto-%s","title":"Produto"},"unit_price":%s,"quantity":%d,"total":999}],
          "payment":{"method":"%s","status":"approved","transaction_id":"t"},
          "shipment":{"carrier":"Correios"},"metadata":{"source":"app"}}
          """.formatted(id,date,status,customer,seller,id,price,quantity,method);
    }
    HttpResponse<String> request(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + api.port() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonObject get(String path) throws Exception {
        var response = request(path); assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    @Test void paginationAndStableDateOrdering() throws Exception {
        var first = get("/orders?size=1");
        assertEquals("B", first.getAsJsonArray("data").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals(3, first.getAsJsonObject("pagination").get("total_elements").getAsInt());
        assertEquals("C", get("/orders?size=1&page=2").getAsJsonArray("data").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals("A", get("/orders?sort=created_at,asc").getAsJsonArray("data").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals(0, get("/orders?page=2147483647&size=100").getAsJsonArray("data").size());
    }
    @Test void allFiltersAndCombinedFilters() throws Exception {
        assertEquals(2, get("/orders?customer.id=1").getAsJsonArray("data").size());
        assertEquals(1, get("/orders?product.id=produto-B").getAsJsonArray("data").size());
        assertEquals(1, get("/orders?status=canceled").getAsJsonArray("data").size());
        assertEquals(2, get("/orders?seller.id=55").getAsJsonArray("data").size());
        assertEquals(1, get("/orders?customer.id=1&seller.id=55&status=paid&product.id=produto-A").getAsJsonArray("data").size());
        assertEquals(0, get("/orders?customer.id=999").getAsJsonArray("data").size());
    }
    @Test void ordersCanBeFilteredByDateOrTimestamp() throws Exception {
        var day = get("/orders?start_date=2026-09-02&end_date=2026-09-02&sort=created_at,asc");
        assertEquals(2, day.getAsJsonArray("data").size());
        assertEquals(2, day.getAsJsonObject("pagination").get("total_elements").getAsInt());
        assertEquals("B", day.getAsJsonArray("data").get(0).getAsJsonObject().get("uuid").getAsString());

        var instant = get("/orders?start_date=2026-09-01T10:00:00Z&end_date=2026-09-01T10:00:00Z");
        assertEquals(1, instant.getAsJsonArray("data").size());
        assertEquals("A", instant.getAsJsonArray("data").get(0).getAsJsonObject().get("uuid").getAsString());
    }
    @Test void detailAndItemsPreservePayloadAndComputeTotals() throws Exception {
        var order = get("/orders/A");
        assertEquals(new BigDecimal("20.50"), order.get("total").getAsBigDecimal());
        assertTrue(order.has("customer") && order.has("seller") && order.has("shipment") && order.has("payment") && order.has("metadata"));
        var items = get("/orders/A/items");
        assertEquals(1, items.size());
        assertEquals(order.get("items"), items.get("items"));
        assertEquals(new BigDecimal("20.50"), items.getAsJsonArray("items").get(0).getAsJsonObject().get("total").getAsBigDecimal());
        // A API deve calcular novamente usando os dados relacionais, nao o total do JSON.
        try (var c = java.sql.DriverManager.getConnection(url, "sa", ""); var s = c.createStatement()) {
            s.executeUpdate("UPDATE item_pedido SET quantidade=4 WHERE pedido_uuid='A'");
        }
        assertEquals(new BigDecimal("41.00"), get("/orders/A").get("total").getAsBigDecimal());
    }
    @Test void summaryAggregatesAllPagesAndFiltersDates() throws Exception {
        var all = get("/orders/financial-summary");
        assertEquals(3, all.get("total_orders").getAsInt());
        assertEquals(new BigDecimal("30.80"), all.get("total_revenue").getAsBigDecimal());
        assertEquals(new BigDecimal("10.27"), all.get("average_order_value").getAsBigDecimal());
        assertEquals(1, all.getAsJsonObject("by_status").get("canceled").getAsInt());
        assertEquals(new BigDecimal("21.50"), all.getAsJsonObject("by_payment_method").getAsJsonObject("pix").get("total").getAsBigDecimal());
        assertEquals(2, get("/orders/financial-summary?seller.id=55").get("total_orders").getAsInt());
        assertEquals(2, get("/orders/financial-summary?start_date=2026-09-02&end_date=2026-09-02").get("total_orders").getAsInt());
        assertEquals(1, get("/orders/financial-summary?seller.id=55&start_date=2026-09-02T10:00:00Z&end_date=2026-09-02T10:00:00Z").get("total_orders").getAsInt());
        var empty = get("/orders/financial-summary?seller.id=999");
        assertEquals(0, empty.get("total_orders").getAsInt());
        assertEquals(new BigDecimal("0.00"), empty.get("average_order_value").getAsBigDecimal());
    }
    @Test void errorsAreJsonAndHaveCorrectHttpStatus() throws Exception {
        for (String path : new String[]{"/orders?page=0", "/orders?size=101", "/orders?sort=bad", "/orders?page=x",
                "/orders?page=1&page=2", "/orders?foo=1", "/orders?start_date=bad",
                "/orders?start_date=2026-09-03&end_date=2026-09-01", "/orders/financial-summary?start_date=bad",
                "/orders/financial-summary?start_date=2026-09-03&end_date=2026-09-01"})
            assertEquals(400, request(path).statusCode(), path);
        assertEquals(404, request("/orders/missing").statusCode());
        assertEquals(404, request("/orders/missing/items").statusCode());
        assertEquals(404, request("/unknown").statusCode());
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + api.port() + "/orders"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        assertEquals("method_not_allowed", JsonParser.parseString(response.body()).getAsJsonObject().get("error").getAsString());
    }

    @Test void unrelatedPayloadsAreNotLoadedAndFiltersAreParameterized() throws Exception {
        try (var c = java.sql.DriverManager.getConnection(url,"sa",""); var s = c.createStatement()) {
            s.executeUpdate("UPDATE pedido SET payload='invalid JSON' WHERE uuid='B'");
        }
        assertEquals("A",get("/orders?customer.id=1&size=1&sort=created_at,asc").getAsJsonArray("data").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals("A",get("/orders/A").get("uuid").getAsString());
        assertEquals(3,get("/orders/financial-summary").get("total_orders").getAsInt());
        assertEquals(0,get("/orders?customer.id=%27%20OR%201%3D1--").getAsJsonArray("data").size());
    }
}
