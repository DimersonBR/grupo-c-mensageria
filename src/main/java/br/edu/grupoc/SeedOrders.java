package br.edu.grupoc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

final class SeedOrders {
    private SeedOrders() { }

    private record Customer(String id, String name, String email, String document) { }
    private record Product(String id, String title, String categoryId, String categoryName,
                           String subCategoryId, String subCategoryName, String price) { }
    private record Seller(String id, String name, String city, String state) { }

    private static final List<Customer> CUSTOMERS = List.of(
            new Customer("7788", "Maria Oliveira", "maria.oliveira@example.com", "987.654.321-00"),
            new Customer("7790", "Joao Pereira", "joao.pereira@example.com", "123.456.789-09"),
            new Customer("7801", "Ana Souza", "ana.souza@example.com", "456.789.123-11"),
            new Customer("7822", "Carlos Lima", "carlos.lima@example.com", "321.654.987-22"),
            new Customer("7835", "Beatriz Ramos", "beatriz.ramos@example.com", "654.321.789-33"),
            new Customer("7846", "Diego Martins", "diego.martins@example.com", "789.123.456-44"));

    private static final List<Product> PRODUCTS = List.of(
            new Product("abc-1344", "Televisao 55 polegadas", "ELEC", "Eletronicos", "TV", "Televisores", "2500.00"),
            new Product("abc-2011", "Smartphone 128GB", "ELEC", "Eletronicos", "PHONE", "Smartphones", "1899.90"),
            new Product("abc-3077", "Notebook 16GB", "ELEC", "Eletronicos", "NOTE", "Notebooks", "4290.00"),
            new Product("cas-0912", "Cafeteira expresso", "HOME", "Casa", "KITCHEN", "Cozinha", "689.50"),
            new Product("cas-1203", "Aspirador robo", "HOME", "Casa", "CLEAN", "Limpeza", "1350.00"),
            new Product("liv-4410", "Livro de Java", "BOOK", "Livros", "TECH", "Tecnologia", "129.90"),
            new Product("esp-7781", "Tenis de corrida", "SPORT", "Esportes", "RUN", "Corrida", "459.00"),
            new Product("esp-8890", "Bicicleta aro 29", "SPORT", "Esportes", "BIKE", "Ciclismo", "3199.00"));

    private static final List<Seller> SELLERS = List.of(
            new Seller("55", "Tech Store", "Sao Paulo", "SP"),
            new Seller("66", "Casa e Cia", "Campinas", "SP"),
            new Seller("77", "Mundo Esportivo", "Belo Horizonte", "MG"));

    private static final List<String> STATUSES = List.of("created", "paid", "shipped", "delivered", "canceled");
    private static final List<String> METHODS = List.of("pix", "credit_card", "boleto");
    private static final List<String> CHANNELS = List.of("mobile_app", "web", "marketplace");
    private static final List<String> CARRIERS = List.of("Correios", "Jadlog", "Loggi");

    static void run(OrderRepository repository, int quantity, long seed) throws Exception {
        Random random = new Random(seed);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90).withNano(0);
        int inserted = 0;
        for (int index = 1; index <= quantity; index++) {
            if (repository.save(order(random, start, seed, index))) inserted++;
        }
        System.out.println("Pedidos ficticios solicitados: " + quantity
                + " | gravados agora: " + inserted
                + " | ja existentes: " + (quantity - inserted));
    }

    private static String order(Random random, OffsetDateTime start, long seed, int index) {
        Customer customer = pick(random, CUSTOMERS);
        Seller seller = pick(random, SELLERS);
        OffsetDateTime created = start.plusHours(random.nextInt(90 * 24)).plusMinutes(random.nextInt(60));

        JsonObject order = new JsonObject();
        order.addProperty("uuid", String.format("SEED-%d-%04d", seed, index));
        order.addProperty("created_at", created.toString());
        order.addProperty("channel", pick(random, CHANNELS));
        order.addProperty("status", pick(random, STATUSES));

        JsonObject customerNode = new JsonObject();
        customerNode.addProperty("id", customer.id());
        customerNode.addProperty("name", customer.name());
        customerNode.addProperty("email", customer.email());
        customerNode.addProperty("document", customer.document());
        order.add("customer", customerNode);

        JsonObject sellerNode = new JsonObject();
        sellerNode.addProperty("id", seller.id());
        sellerNode.addProperty("name", seller.name());
        sellerNode.addProperty("city", seller.city());
        sellerNode.addProperty("state", seller.state());
        order.add("seller", sellerNode);

        JsonArray items = new JsonArray();
        int total = 1 + random.nextInt(3);
        for (int item = 1; item <= total; item++) {
            items.add(item(random, PRODUCTS.get((index + item) % PRODUCTS.size()), item));
        }
        order.add("items", items);

        JsonObject payment = new JsonObject();
        payment.addProperty("method", pick(random, METHODS));
        payment.addProperty("status", order.get("status").getAsString().equals("canceled") ? "refused" : "approved");
        payment.addProperty("transaction_id", "pay_" + (100000 + random.nextInt(899999)));
        order.add("payment", payment);

        JsonObject shipment = new JsonObject();
        shipment.addProperty("carrier", pick(random, CARRIERS));
        shipment.addProperty("service", "expresso");
        shipment.addProperty("status", order.get("status").getAsString());
        shipment.addProperty("tracking_code", "BR" + (100000000 + random.nextInt(899999999)));
        order.add("shipment", shipment);

        JsonObject metadata = new JsonObject();
        metadata.addProperty("source", "seed");
        metadata.addProperty("user_agent", "dados-ficticios/1.0");
        metadata.addProperty("ip_address", "10.0.0." + (1 + random.nextInt(250)));
        order.add("metadata", metadata);

        return order.toString();
    }

    private static JsonObject item(Random random, Product product, int id) {
        BigDecimal discount = BigDecimal.valueOf(90 + random.nextInt(21)).divide(BigDecimal.valueOf(100));
        BigDecimal price = new BigDecimal(product.price()).multiply(discount).setScale(2, RoundingMode.HALF_UP);

        JsonObject productNode = new JsonObject();
        productNode.addProperty("id", product.id());
        productNode.addProperty("title", product.title());

        JsonObject subCategory = new JsonObject();
        subCategory.addProperty("id", product.subCategoryId());
        subCategory.addProperty("name", product.subCategoryName());

        JsonObject category = new JsonObject();
        category.addProperty("id", product.categoryId());
        category.addProperty("name", product.categoryName());
        category.add("sub_category", subCategory);

        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.add("product", productNode);
        item.addProperty("unit_price", price);
        item.addProperty("quantity", 1 + random.nextInt(3));
        item.add("category", category);
        return item;
    }

    private static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
