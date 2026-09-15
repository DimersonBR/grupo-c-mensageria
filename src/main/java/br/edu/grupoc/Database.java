package br.edu.grupoc;

final class Database {
    private Database() { }
    static String url() {
        return System.getenv().getOrDefault("ORDERS_DB_URL",
                "jdbc:h2:file:./data/pedidos;AUTO_SERVER=TRUE;WRITE_DELAY=0");
    }
}
