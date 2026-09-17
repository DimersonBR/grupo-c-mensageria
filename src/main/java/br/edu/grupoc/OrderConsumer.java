package br.edu.grupoc;

public final class OrderConsumer {
    private static final int SEED_PADRAO = 50;

    public static void run(String mode, String[] options) throws Exception {
        if (!mode.equals("--semear") && options.length > 0) {
            throw new IllegalArgumentException("O comando " + mode + " nao aceita argumentos adicionais.");
        }
        try (var repository = new OrderRepository(Database.url())) {
            switch (mode) {
                case "--listar-pedidos" -> repository.list();
                case "--demo-pedidos" -> demo(repository);
                case "--semear" -> {
                    SeedOrders.run(repository, quantity(options), seed(options));
                    repository.list();
                }
                default -> PollingOrders.run(repository, Credentials.load(), subscription());
            }
        }
    }

    private static void demo(OrderRepository repository) throws Exception {
        try (var input = OrderConsumer.class.getResourceAsStream("/pedido-exemplo.json")) {
            String payload = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            System.out.println(repository.save(payload) ? "Pedido de exemplo salvo." : "Pedido de exemplo ja existente.");
            repository.list();
        }
    }

    private static String subscription() {
        return System.getenv().getOrDefault("ORDERS_SUBSCRIPTION", "projects/serjava-demo/subscriptions/grupo-c");
    }

    private static int quantity(String[] options) {
        if (options.length == 0) return SEED_PADRAO;
        try {
            int quantity = Integer.parseInt(options[0]);
            if (quantity < 1 || quantity > 5000) throw new NumberFormatException();
            return quantity;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A quantidade de pedidos ficticios deve ser um inteiro entre 1 e 5000.");
        }
    }

    private static long seed(String[] options) {
        if (options.length < 2) return 1;
        try {
            return Long.parseLong(options[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A semente dos dados ficticios deve ser um numero inteiro.");
        }
    }
}
