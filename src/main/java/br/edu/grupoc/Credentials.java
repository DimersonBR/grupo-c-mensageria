package br.edu.grupoc;

import com.google.auth.oauth2.ServiceAccountCredentials;
import java.nio.file.Files;
import java.nio.file.Path;

/** Localiza e carrega a conta de servico usada para autenticar no Google Cloud. */
final class Credentials {
    // Classe utilitaria: nao precisa ser instanciada.
    private Credentials() { }

    static Path path() {
        // A variavel de ambiente permite usar uma credencial fora da pasta do projeto.
        return Path.of(System.getenv().getOrDefault("ORDERS_CREDENTIAL", "sa-grupo-c-key.json"));
    }

    static ServiceAccountCredentials load() throws Exception {
        Path path = path();
        // Valida o arquivo antes de entrega-lo a biblioteca para produzir um erro mais claro.
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("Credencial nao encontrada em " + path.toAbsolutePath()
                    + ". Peca o arquivo ao grupo ou use --semear para popular o banco com dados ficticios.");
        }
        // O try-with-resources fecha o arquivo mesmo se o JSON da credencial for invalido.
        try (var input = Files.newInputStream(path)) {
            return ServiceAccountCredentials.fromStream(input);
        }
    }
}
