package br.edu.grupoc;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/** Filtros, ordenacao, pagina e agregacoes executados pelo H2. */
final class OrderQueries {
    private final String url;
    OrderQueries(String url) { this.url = url; }

    private Connection connect() throws SQLException {
        var connection = DriverManager.getConnection(url, "sa", "");
        // A pagina e sua contagem devem enxergar o mesmo estado do banco.
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
        return connection;
    }

    JsonObject page(Map<String,String> filters, int page, int size, String sort) throws SQLException {
        return page(filters, page, size, sort, null, null);
    }

    JsonObject page(Map<String,String> filters, int page, int size, String sort, Instant start, Instant end) throws SQLException {
        // O mesmo filtro alimenta tanto a contagem total quanto a consulta paginada.
        Filter where = filter(filters, start, end);
        try (var connection = connect()) {
            long count;
            try (var statement = prepare(connection, "SELECT COUNT(*) FROM pedido p" + where.sql, where.values);
                 var rows = statement.executeQuery()) { rows.next(); count = rows.getLong(1); }
            String direction = sort.equals("created_at,asc") ? "ASC" : "DESC";
            List<Object> parameters = new ArrayList<>(where.values);
            parameters.add(size); parameters.add(((long) page - 1) * size);
            JsonArray data = new JsonArray();
            // O UUID desempata pedidos com a mesma data e mantem a paginacao estavel.
            try (var statement = prepare(connection, "SELECT p.uuid,p.payload FROM pedido p" + where.sql
                    + " ORDER BY p.criado_em " + direction + ",p.uuid ASC LIMIT ? OFFSET ?", parameters);
                 var rows = statement.executeQuery()) {
                while (rows.next()) data.add(hydrate(connection, rows.getString("uuid"), rows.getString("payload")));
            }
            JsonObject pagination = new JsonObject();
            pagination.addProperty("page", page); pagination.addProperty("size", size);
            pagination.addProperty("total_elements", count); pagination.addProperty("total_pages", (count + size - 1) / size);
            JsonObject response = new JsonObject(); response.add("data", data); response.add("pagination", pagination);
            connection.commit(); return response;
        }
    }

    Optional<JsonObject> detail(String uuid) throws SQLException {
        try (var connection = connect();
             var statement = prepare(connection, "SELECT payload FROM pedido WHERE uuid=?", List.of(uuid));
             var rows = statement.executeQuery()) {
            Optional<JsonObject> result = rows.next() ? Optional.of(hydrate(connection, uuid, rows.getString(1))) : Optional.empty();
            connection.commit(); return result;
        }
    }

    private JsonObject hydrate(Connection connection, String uuid, String payload) throws SQLException {
        // O payload preserva os campos originais; valores financeiros sao recalculados
        // com as colunas relacionais, que sao a fonte de verdade para as consultas.
        JsonObject order = JsonParser.parseString(payload).getAsJsonObject();
        Map<String,JsonObject> items = new HashMap<>();
        for (var item : order.getAsJsonArray("items")) items.put(item.getAsJsonObject().get("id").getAsString(), item.getAsJsonObject());
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        try (var statement = prepare(connection, "SELECT id,preco_unitario,quantidade FROM item_pedido WHERE pedido_uuid=?", List.of(uuid));
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                BigDecimal price = rows.getBigDecimal(2);
                int quantity = rows.getInt(3);
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
                JsonObject item = items.get(rows.getString(1));
                if (item == null) throw new SQLException("Item sem representacao no payload");
                item.addProperty("unit_price", price); item.addProperty("quantity", quantity); item.addProperty("total", subtotal);
                total = total.add(subtotal);
            }
        }
        order.addProperty("total", total); return order;
    }

    JsonObject summary(Map<String,String> filters, Instant start, Instant end) throws SQLException {
        Filter where = filter(filters, start, end);
        // Cada pedido contribui uma unica vez para a contagem, mesmo com varios itens.
        String sql = "SELECT status,method,COUNT(*) quantity,SUM(total) revenue FROM ("
                + "SELECT p.status,COALESCE(p.payment_method,'unknown') method,"
                + "COALESCE((SELECT SUM(i.preco_unitario*i.quantidade) FROM item_pedido i WHERE i.pedido_uuid=p.uuid),0) total "
                + "FROM pedido p" + where.sql + ") totals GROUP BY status,method";
        JsonObject statuses = new JsonObject(), methods = new JsonObject();
        for (String status : List.of("created","paid","shipped","delivered","canceled")) statuses.addProperty(status, 0);
        long count = 0;
        BigDecimal revenue = BigDecimal.ZERO.setScale(2);
        try (var connection = connect(); var statement = prepare(connection, sql, where.values); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String status = rows.getString(1), method = rows.getString(2);
                long quantity = rows.getLong(3);
                BigDecimal total = rows.getBigDecimal(4).setScale(2);
                count += quantity; revenue = revenue.add(total);
                statuses.addProperty(status, (statuses.has(status) ? statuses.get(status).getAsLong() : 0) + quantity);
                if (!methods.has(method)) {
                    JsonObject bucket = new JsonObject(); bucket.addProperty("count", 0); bucket.addProperty("total", BigDecimal.ZERO.setScale(2));
                    methods.add(method,bucket);
                }
                JsonObject bucket = methods.getAsJsonObject(method);
                bucket.addProperty("count", bucket.get("count").getAsLong() + quantity);
                bucket.addProperty("total", bucket.get("total").getAsBigDecimal().add(total));
            }
            connection.commit();
        }
        JsonObject result = new JsonObject(); result.addProperty("total_orders", count); result.addProperty("total_revenue", revenue);
        result.addProperty("average_order_value", count == 0 ? BigDecimal.ZERO.setScale(2) : revenue.divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP));
        result.add("by_status",statuses); result.add("by_payment_method",methods); return result;
    }

    private record Filter(String sql,List<Object> values) { }
    private Filter filter(Map<String,String> filters, Instant start, Instant end) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> values = new ArrayList<>();
        // Somente chaves conhecidas podem virar colunas; os valores continuam parametrizados.
        for (var entry : Map.of("customer.id","p.cliente_id","seller.id","p.seller_id","status","p.status").entrySet()) {
            if (filters.containsKey(entry.getKey())) {
                sql.append(" AND ").append(entry.getValue()).append("=?"); values.add(filters.get(entry.getKey()));
            }
        }
        if (filters.containsKey("product.id")) {
            // EXISTS filtra pelo produto sem multiplicar um pedido que contenha varios itens.
            sql.append(" AND EXISTS (SELECT 1 FROM item_pedido f WHERE f.pedido_uuid=p.uuid AND f.produto_id=?)");
            values.add(filters.get("product.id"));
        }
        if (start != null) { sql.append(" AND p.criado_em>=?"); values.add(start.atOffset(ZoneOffset.UTC)); }
        if (end != null) { sql.append(" AND p.criado_em<=?"); values.add(end.atOffset(ZoneOffset.UTC)); }
        return new Filter(sql.toString(),values);
    }
    private PreparedStatement prepare(Connection connection,String sql,List<?> values) throws SQLException {
        // PreparedStatement separa os dados do SQL e evita injecao pelos filtros da API.
        var statement = connection.prepareStatement(sql);
        try {
            for (int i=0;i<values.size();i++) statement.setObject(i+1,values.get(i));
            return statement;
        } catch (SQLException e) { statement.close(); throw e; }
    }
}
