package br.edu.grupoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SeedOrdersTest {
    @TempDir Path directory;

    String url() { return "jdbc:h2:file:" + directory.resolve("seed").toAbsolutePath(); }

    @Test void generatesQueryableOrdersAndDoesNotDuplicate() throws Exception {
        try (var repository = new OrderRepository(url())) {
            SeedOrders.run(repository, 25, 3);
            SeedOrders.run(repository, 25, 3);
        }
        var page = new OrderQueries(url()).page(Map.of(), 1, 100, "created_at,desc");
        assertEquals(25, page.getAsJsonObject("pagination").get("total_elements").getAsInt());
        var order = page.getAsJsonArray("data").get(0).getAsJsonObject();
        for (String field : new String[]{"uuid", "created_at", "channel", "status", "customer", "seller", "items", "payment", "total"}) {
            assertTrue(order.has(field), field);
        }
        assertTrue(order.get("total").getAsBigDecimal().compareTo(BigDecimal.ZERO) > 0);

        var summary = new OrderQueries(url()).summary(Map.of(), null, null);
        assertEquals(25, summary.get("total_orders").getAsInt());
        assertTrue(summary.get("total_revenue").getAsBigDecimal().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test void differentSeedsGenerateDifferentOrders() throws Exception {
        try (var repository = new OrderRepository(url())) {
            SeedOrders.run(repository, 10, 1);
            SeedOrders.run(repository, 10, 2);
        }
        assertEquals(20, new OrderQueries(url()).summary(Map.of(), null, null).get("total_orders").getAsInt());
    }
}
