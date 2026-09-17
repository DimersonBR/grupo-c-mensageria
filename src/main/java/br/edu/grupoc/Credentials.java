package br.edu.grupoc;

import com.google.auth.oauth2.ServiceAccountCredentials;
import java.nio.file.Files;
import java.nio.file.Path;

final class Credentials {
    private Credentials() { }

    static Path path() {
        return Path.of(System.getenv().getOrDefault("ORDERS_CREDENTIAL", "sa-grupo-c-key.json"));
    }

    static ServiceAccountCredentials load() throws Exception {
        Path path = path();
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("Credencial nao encontrada em " + path.toAbsolutePath()
                    + ". Peca o arquivo ao grupo ou use --semear para popular o banco com dados ficticios.");
        }
        try (var input = Files.newInputStream(path)) {
            return ServiceAccountCredentials.fromStream(input);
        }
    }
}
