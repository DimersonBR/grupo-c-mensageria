package br.edu.grupoc;

import com.google.auth.oauth2.ServiceAccountCredentials;


import java.nio.file.Files;
import java.nio.file.Path;


public final class OrderConsumer {
    public static void run(String mode) throws Exception {
        String url = Database.url();
        try (var repository = new OrderRepository(url)) {
            if (mode.equals("--listar-pedidos")) { repository.list(); return; }
            if (mode.equals("--demo-pedidos")) {
                try (var input = OrderConsumer.class.getResourceAsStream("/pedido-exemplo.json")) {
                    String payload = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    System.out.println(repository.save(payload) ? "Pedido de exemplo salvo." : "Pedido de exemplo ja existente.");
                    repository.list();
                }
                return;
            }
            ServiceAccountCredentials credentials;
            try (var input = Files.newInputStream(Path.of("sa-grupo-c-key.json"))) {
                credentials = ServiceAccountCredentials.fromStream(input);
            }
            String subscription = System.getenv().getOrDefault("ORDERS_SUBSCRIPTION",
                    "projects/serjava-demo/subscriptions/grupo-c");
            PollingOrders.run(repository, credentials, subscription);
        }
    }
}

