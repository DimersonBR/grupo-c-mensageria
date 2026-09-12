package br.edu.grupoc;

import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.HashSet;

/** Uma transacao por pedido. O UUID protege contra entregas repetidas. */
public final class OrderRepository implements AutoCloseable {
    private final Connection connection;

    public OrderRepository(String url) throws Exception {
        connection = DriverManager.getConnection(url, "sa", "");
        try (var stream = getClass().getResourceAsStream("/schema.sql")) {
            if (stream == null) throw new IllegalStateException("schema.sql ausente");
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            try (var statement = connection.createStatement()) {
                for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
            }
        } catch (Exception e) {
            connection.close();
            throw e;
        }
    }

    public synchronized boolean save(String payload) throws Exception {
        JsonObject order = JsonParser.parseString(payload).getAsJsonObject();
        String uuid = text(order, "uuid");
        JsonObject customer = order.getAsJsonObject("customer");
        String customerId = text(customer, "id");
        String name = text(customer, "name"), email = text(customer, "email");
        String document = text(customer, "document");
        OffsetDateTime created = OffsetDateTime.parse(text(order, "created_at"));
        String channel = text(order, "channel"), status = text(order, "status");
        JsonArray items = order.getAsJsonArray("items");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items vazio");
        HashSet<String> ids = new HashSet<>();
        for (var element : items) {
            JsonObject item = element.getAsJsonObject();
            if (!ids.add(text(item, "id"))) throw new IllegalArgumentException("Item repetido");
            text(item.getAsJsonObject("product"), "id");
            text(item.getAsJsonObject("product"), "title");
            BigDecimal price = new BigDecimal(text(item, "unit_price")).setScale(2);
            int quantity = new BigDecimal(text(item, "quantity")).intValueExact();
            if (price.signum() < 0 || price.precision() > 19 || quantity <= 0)
                throw new IllegalArgumentException("Preco ou quantidade invalida");
        }
        connection.setAutoCommit(false);
        try {
            try (var query = connection.prepareStatement("SELECT uuid FROM pedido WHERE uuid = ?")) {
                query.setString(1, uuid);
                try (var rows = query.executeQuery()) {
                    if (rows.next()) { connection.rollback(); return false; }
                }
            }
            execute("MERGE INTO cliente (id,nome,email,documento) KEY(id) VALUES (?,?,?,?)",
                    customerId, name, email, document);
            execute("INSERT INTO pedido (uuid,cliente_id,criado_em,canal,status,payload) VALUES (?,?,?,?,?,?)",
                    uuid, customerId, created, channel, status, payload);
            for (var element : items) {
                JsonObject item = element.getAsJsonObject();
                JsonObject product = item.getAsJsonObject("product");
                String productId = text(product, "id");
                execute("MERGE INTO produto (id,titulo) KEY(id) VALUES (?,?)", productId, text(product,"title"));
                JsonObject category = item.getAsJsonObject("category");
                JsonObject sub = category == null ? null : category.getAsJsonObject("sub_category");
                execute("INSERT INTO item_pedido VALUES (?,?,?,?,?,?,?,?,?)", uuid, text(item,"id"), productId,
                        new BigDecimal(text(item,"unit_price")), new BigDecimal(text(item,"quantity")).intValueExact(),
                        optional(category,"id"), optional(category,"name"), optional(sub,"id"), optional(sub,"name"));
            }
            connection.commit();
            return true;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static String text(JsonObject object, String key) {
        String value = optional(object, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Campo obrigatorio: " + key);
        return value;
    }

    private static String optional(JsonObject object, String key) {
        return object == null || !object.has(key) || object.get(key).isJsonNull() ? null : object.get(key).getAsString();
    }

    public synchronized void list() throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT p.uuid,p.status,p.indexado_em,COUNT(i.id) itens,SUM(i.preco_unitario*i.quantidade) total "
                + "FROM pedido p JOIN item_pedido i ON p.uuid=i.pedido_uuid GROUP BY p.uuid ORDER BY p.indexado_em")) {
            int count = 0;
            while (rows.next()) {
                count++;
                System.out.printf("%s | %s | indexado: %s | itens: %d | total: %s%n",
                        rows.getString(1), rows.getString(2), rows.getObject(3), rows.getInt(4), rows.getBigDecimal(5));
            }
            System.out.println("Pedidos no banco: " + count);
        }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
