package br.edu.grupoc;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SharedDatabaseTest {
    @TempDir Path directory;
    static String sample(String id) throws Exception {
        try (var input = SharedDatabaseTest.class.getResourceAsStream("/pedido-exemplo.json")) {
            var order = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            order.addProperty("uuid",id); return order.toString();
        }
    }

    @Test void anotherJavaProcessCanWriteWhileApiReads() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("shared") + ";AUTO_SERVER=TRUE;WRITE_DELAY=0";
        try (var repository = new OrderRepository(url); var api = new OrdersApi(url,0)) {
            repository.save(sample("first"));
            api.start();
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + api.port() + "/orders");
            assertEquals(1, count(client,endpoint));
            Path log = directory.resolve("writer.log");
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    Writer.class.getName(), url).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                // A leitura HTTP ocorre enquanto o outro processo inicia a conexao e grava.
                assertTrue(count(client,endpoint) >= 1);
                assertTrue(process.waitFor(30,TimeUnit.SECONDS), "O processo escritor nao encerrou");
                assertEquals(0,process.exitValue(),Files.readString(log));
                assertEquals(2,count(client,endpoint));
                assertFalse(repository.save(sample("second")));
            } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); } }
        }
        var reopened = new OrderRepository(url);
        try (reopened) {
            assertEquals(2,new OrderQueries(url).page(Map.of(),1,20,"created_at,desc").getAsJsonObject("pagination").get("total_elements").getAsInt());
        }
    }
    private int count(HttpClient client, URI uri) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("pagination").get("total_elements").getAsInt();
    }
    public static class Writer {
        public static void main(String[] args) throws Exception {
            try (var repository = new OrderRepository(args[0])) { repository.save(sample("second")); }
        }
    }

    @Test void migratesLegacyRowsWithoutChangingPayloadOrIndexTime() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("legacy");
        String payload = sample("legacy-order");
        try (var connection = DriverManager.getConnection(url,"sa",""); var statement = connection.createStatement()) {
            String schema;
            try (var input = getClass().getResourceAsStream("/schema.sql")) {
                schema = new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("ALTER TABLE")[0];
            }
            for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
            statement.execute("INSERT INTO cliente VALUES ('7788','Maria','maria@example.com','123')");
            statement.execute("INSERT INTO produto VALUES ('abc-1344','TV')");
            try (var insert = connection.prepareStatement("INSERT INTO pedido VALUES ('legacy-order','7788','2025-10-01T10:15:00Z','2026-09-01T12:00:00Z','mobile_app','created',?)")) {
                insert.setString(1,payload); insert.executeUpdate();
            }
            statement.execute("INSERT INTO item_pedido(pedido_uuid,id,produto_id,preco_unitario,quantidade) VALUES ('legacy-order','1','abc-1344',2500,2)");
        }
        for (int i=0;i<2;i++) {
            var repository = new OrderRepository(url);
            try (repository) {
                var result = new OrderQueries(url).summary(Map.of("seller.id","55"),null,null);
                assertEquals(1,result.get("total_orders").getAsInt());
                assertEquals(1,result.getAsJsonObject("by_payment_method").getAsJsonObject("pix").get("count").getAsInt());
            }
        }
        try (var connection = DriverManager.getConnection(url,"sa",""); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT payload,indexado_em,projection_version FROM pedido")) {
            assertTrue(rows.next()); assertEquals(payload,rows.getString(1));
            assertEquals(java.time.Instant.parse("2026-09-01T12:00:00Z"),rows.getObject(2,java.time.OffsetDateTime.class).toInstant());
            assertEquals(1,rows.getInt(3)); assertFalse(rows.next());
        }
    }
}
