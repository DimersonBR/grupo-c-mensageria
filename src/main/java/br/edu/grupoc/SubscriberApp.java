package br.edu.grupoc;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Consulta a assinatura do grupo C usando a biblioteca oficial do Google. */
public final class SubscriberApp {
    private static final String SUBSCRIPTION = "projects/serjava-demo/subscriptions/grupo-c";
    private static final String PERMISSION = "pubsub.subscriptions.consume";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (java.net.BindException e) {
            String port = System.getenv().getOrDefault("ORDERS_API_PORT", "8080");
            System.err.println("Nao foi possivel iniciar a API: a porta " + port + " esta ocupada ou indisponivel.");
            System.err.println("Se a API ja estiver aberta, use http://127.0.0.1:" + port + "/orders");
            System.err.println("Ou escolha outra porta no PowerShell: $env:ORDERS_API_PORT = '8081'");
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(e.getMessage() == null ? "Argumento invalido." : e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            // Nao imprime credenciais ou respostas de autenticacao.
            // Tambem omite detalhes sensiveis da excecao e mostra apenas seu tipo.
            System.err.println("Falha (" + e.getClass().getSimpleName()
                    + "). Verifique a credencial, a rede e as permissoes.");
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        // Os comandos da API e do banco possuem seus proprios fluxos de execucao.
        if (args.length == 1 && args[0].equals("--api")) {
            OrdersApi.run();
            return;
        }
        if (args.length >= 1 && java.util.Set.of("--pedidos", "--demo-pedidos", "--listar-pedidos", "--semear").contains(args[0])) {
            OrderConsumer.run(args[0], java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        // Sem --receber, o comportamento seguro e apenas conferir a permissao IAM.
        boolean receive = false;
        boolean acknowledge = false;
        for (String arg : args) {
            switch (arg) {
                case "--receber" -> receive = true;
                case "--confirmar" -> acknowledge = true;
                case "--help" -> {
                    System.out.println("Uso: SubscriberApp --api | --pedidos | --listar-pedidos | --demo-pedidos"
                            + " | --semear [quantidade] [semente] | [--receber [--confirmar]]");
                    return;
                }
                default -> throw new IllegalArgumentException("Argumento desconhecido");
            }
        }
        if (acknowledge && !receive) {
            System.err.println("--confirmar exige --receber.");
            throw new IllegalArgumentException();
        }

        ServiceAccountCredentials credentials = Credentials.load();
        SubscriptionAdminSettings settings = SubscriptionAdminSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
        try (SubscriptionAdminClient client = SubscriptionAdminClient.create(settings)) {
            if (!receive) {
                // Testar a permissao nao retira mensagens da assinatura.
                var result = await(client.testIamPermissionsCallable().futureCall(
                        TestIamPermissionsRequest.newBuilder().setResource(SUBSCRIPTION)
                                .addPermissions(PERMISSION).build()));
                if (!result.getPermissionsList().contains(PERMISSION)) {
                    throw new IllegalStateException("Sem permissao de consumo");
                }
                System.out.println("Conexao autenticada. Permissao para receber mensagens confirmada.");
                System.out.println("Nenhuma mensagem foi lida ou confirmada.");
                return;
            }

            PullResponse response;
            try {
                // Limita cada leitura a dez mensagens para manter a saida controlada.
                response = await(client.pullCallable().futureCall(PullRequest.newBuilder()
                        .setSubscription(SUBSCRIPTION).setMaxMessages(10).build()));
            } catch (TimeoutException e) {
                System.out.println("A consulta terminou sem retornar mensagens; tente novamente.");
                return;
            }
            if (response.getReceivedMessagesCount() == 0) {
                System.out.println("Nenhuma mensagem retornada nesta consulta.");
                return;
            }
            List<String> ids = new ArrayList<>();
            for (var received : response.getReceivedMessagesList()) {
                var message = received.getMessage();
                System.out.println("\nID: " + message.getMessageId());
                System.out.println(message.getData().toStringUtf8());
                if (!message.getAttributesMap().isEmpty()) {
                    System.out.println("Atributos: " + message.getAttributesMap());
                }
                ids.add(received.getAckId());
            }
            System.out.flush();
            // Nao confirma mensagens que talvez nao tenham sido exibidas ao usuario.
            if (System.out.checkError()) {
                throw new IllegalStateException("Falha ao exibir mensagens");
            }
            if (acknowledge) {
                // ACK remove as mensagens desta assinatura somente quando solicitado explicitamente.
                await(client.acknowledgeCallable().futureCall(
                        com.google.pubsub.v1.AcknowledgeRequest.newBuilder()
                                .setSubscription(SUBSCRIPTION).addAllAckIds(ids).build()));
                System.out.println("\n" + ids.size() + " mensagem(ns) confirmada(s).");
            } else {
                // Prazo zero libera imediatamente as mensagens para uma nova entrega.
                await(client.modifyAckDeadlineCallable().futureCall(
                        com.google.pubsub.v1.ModifyAckDeadlineRequest.newBuilder()
                                .setSubscription(SUBSCRIPTION).addAllAckIds(ids)
                                .setAckDeadlineSeconds(0).build()));
                System.out.println("\nMensagens nao confirmadas; disponiveis para nova entrega.");
            }
        }
    }

    private static <T> T await(ApiFuture<T> future) throws Exception {
        // Cancela chamadas demoradas e preserva o sinal de interrupcao da thread.
        try {
            return future.get(25, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw e;
        }
    }
}
