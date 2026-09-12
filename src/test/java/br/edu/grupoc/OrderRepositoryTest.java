package br.edu.grupoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class OrderRepositoryTest {
    @TempDir Path directory;
    String sample() throws Exception {
        try (var input = getClass().getResourceAsStream("/pedido-exemplo.json")) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    String url() { return "jdbc:h2:file:" + directory.resolve("pedidos").toAbsolutePath(); }
    long count(String table) throws Exception {
        try (var c = DriverManager.getConnection(url(), "sa", "");
             var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            r.next(); return r.getLong(1);
        }
    }
    @Test void persistsAndDeduplicatesAfterReopening() throws Exception {
        try (var repository = new OrderRepository(url())) { assertTrue(repository.save(sample())); }
        try (var repository = new OrderRepository(url())) { assertFalse(repository.save(sample())); }
        for (var table : new String[]{"pedido", "cliente", "produto", "item_pedido"}) assertEquals(1, count(table));
        try (var c = DriverManager.getConnection(url(), "sa", ""); var s = c.createStatement();
             var r = s.executeQuery("SELECT p.indexado_em, i.preco_unitario*i.quantidade total FROM pedido p JOIN item_pedido i ON p.uuid=i.pedido_uuid")) {
            assertTrue(r.next()); assertNotNull(r.getObject(1)); assertEquals("5000.00", r.getBigDecimal(2).toPlainString());
        }
    }
    @Test void rejectsInvalidMessages() throws Exception {
        try (var repository = new OrderRepository(url())) {
            assertThrows(Exception.class, () -> repository.save("Mensagem otimista"));
            var order = JsonParser.parseString(sample()).getAsJsonObject();
            order.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("quantity", -1);
            assertThrows(Exception.class, () -> repository.save(order.toString()));
        }
        assertEquals(0, count("pedido")); assertEquals(0, count("cliente"));
    }
    @Test void rollsBackCustomerAndOrderWhenProductInsertFails() throws Exception {
        var order = JsonParser.parseString(sample()).getAsJsonObject();
        order.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("product")
                .addProperty("title", "x".repeat(501));
        try (var repository = new OrderRepository(url())) {
            assertThrows(Exception.class, () -> repository.save(order.toString()));
        }
        for (var table : new String[]{"pedido", "cliente", "produto", "item_pedido"}) assertEquals(0, count(table));
    }
}
