package br.edu.grupoc;

/** Mantem em um unico lugar a configuracao da conexao com o banco local. */
final class Database {
    private Database() { }
    static String url() {
        // AUTO_SERVER permite que a API e o consumidor abram o mesmo arquivo H2.
        // WRITE_DELAY=0 faz cada commit chegar imediatamente ao arquivo compartilhado.
        return System.getenv().getOrDefault("ORDERS_DB_URL",
                "jdbc:h2:file:./data/pedidos;AUTO_SERVER=TRUE;WRITE_DELAY=0");
    }
}
