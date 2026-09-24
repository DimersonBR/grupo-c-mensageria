package br.edu.grupoc;

/** Direciona os comandos relacionados a persistencia e ao consumo de pedidos. */
public final class OrderConsumer {
    // Quantidade usada quando --semear e executado sem argumentos.
    private static final int SEED_PADRAO = 50;

    public static void run(String mode, String[] options) throws Exception {
        // Somente a semeadura recebe quantidade e semente como argumentos extras.
        if (!mode.equals("--semear") && options.length > 0) {
            throw new IllegalArgumentException("O comando " + mode + " nao aceita argumentos adicionais.");
        }
        // A mesma inicializacao abre o banco, aplica o esquema e fecha a conexao ao final.
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
        // O exemplo fica empacotado nos recursos da aplicacao e passa pelo fluxo real de gravacao.
        try (var input = OrderConsumer.class.getResourceAsStream("/pedido-exemplo.json")) {
            String payload = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            System.out.println(repository.save(payload) ? "Pedido de exemplo salvo." : "Pedido de exemplo ja existente.");
            repository.list();
        }
    }

    private static String subscription() {
        // Facilita apontar o consumidor para outra assinatura sem recompilar o projeto.
        return System.getenv().getOrDefault("ORDERS_SUBSCRIPTION", "projects/serjava-demo/subscriptions/grupo-c");
    }

    private static int quantity(String[] options) {
        if (options.length == 0) return SEED_PADRAO;
        try {
            int quantity = Integer.parseInt(options[0]);
            // O limite evita popular o banco acidentalmente com um volume exagerado.
            if (quantity < 1 || quantity > 5000) throw new NumberFormatException();
            return quantity;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A quantidade de pedidos ficticios deve ser um inteiro entre 1 e 5000.");
        }
    }

    private static long seed(String[] options) {
        // Uma semente fixa torna a geracao reproduzivel entre execucoes.
        if (options.length < 2) return 1;
        try {
            return Long.parseLong(options[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A semente dos dados ficticios deve ser um numero inteiro.");
        }
    }
}
