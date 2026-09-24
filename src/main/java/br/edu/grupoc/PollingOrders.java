package br.edu.grupoc;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.pubsub.v1.*;
import java.time.Duration;

/** Consultas independentes: nao utiliza os pings do StreamingPull. */
final class PollingOrders {
    static void run(OrderRepository repository, ServiceAccountCredentials credentials, String subscription) throws Exception {
        var settings = SubscriptionAdminSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials));
        // Os retries automaticos sao desligados para que o laco controle espera e mensagens de erro.
        settings.pullSettings().setRetryableCodes(java.util.Set.of());
        settings.pullSettings().setRetrySettings(settings.pullSettings().getRetrySettings().toBuilder()
                .setInitialRpcTimeoutDuration(Duration.ofSeconds(25))
                .setMaxRpcTimeoutDuration(Duration.ofSeconds(25))
                .setTotalTimeoutDuration(Duration.ofSeconds(25)).build());
        try (var client = SubscriptionAdminClient.create(settings.build())) {
            System.out.println("Consultando pedidos de " + subscription + ". Ctrl+C para parar.");
            while (!Thread.currentThread().isInterrupted()) {
                PullResponse response;
                try {
                    // Uma mensagem por vez simplifica o limite de ACK e a transacao no banco.
                    response = client.pull(PullRequest.newBuilder().setSubscription(subscription).setMaxMessages(1).build());
                } catch (ApiException e) {
                    var code = e.getStatusCode().getCode();
                    if (code == StatusCode.Code.DEADLINE_EXCEEDED) {
                        System.out.println("Consulta sem retorno em 25 segundos; tentando novamente.");
                    } else if (code == StatusCode.Code.UNAVAILABLE || code == StatusCode.Code.RESOURCE_EXHAUSTED) {
                        System.err.println("Pub/Sub indisponivel (" + code + "); nova tentativa em 5 segundos.");
                    } else {
                        System.err.println("Consulta recusada pelo Pub/Sub: " + code);
                        throw e;
                    }
                    Thread.sleep(5000);
                    continue;
                }
                if (response.getReceivedMessagesCount() == 0) {
                    System.out.println("Nenhum pedido retornado nesta consulta. Aguardando novos pedidos...");
                    Thread.sleep(5000);
                }
                for (var received : response.getReceivedMessagesList()) {
                    boolean saved = false;
                    try {
                        // Reserva tempo suficiente para validar e gravar o pedido antes do ACK.
                        await(client.modifyAckDeadlineCallable().futureCall(ModifyAckDeadlineRequest.newBuilder()
                                .setSubscription(subscription).addAckIds(received.getAckId()).setAckDeadlineSeconds(120).build()));
                        boolean inserted = repository.save(received.getMessage().getData().toStringUtf8());
                        saved = true;
                        // O ACK so e enviado depois que a transacao local foi confirmada.
                        await(client.acknowledgeCallable().futureCall(AcknowledgeRequest.newBuilder()
                                .setSubscription(subscription).addAckIds(received.getAckId()).build()));
                        System.out.println("Mensagem " + received.getMessage().getMessageId()
                                + (inserted ? ": pedido salvo; ACK enviado." : ": pedido ja salvo; ACK enviado."));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    } catch (Exception e) {
                        // Sem ACK, o Pub/Sub pode reenviar; o UUID impede uma segunda insercao.
                        System.err.println("Mensagem " + received.getMessage().getMessageId()
                                + (saved ? ": salva, mas ACK nao confirmado" : ": nao salva")
                                + " (" + e.getClass().getSimpleName() + "). Pode ser entregue novamente.");
                        Thread.sleep(5000);
                    }
                }
            }
        }
    }

    private static <T> T await(com.google.api.core.ApiFuture<T> future) throws Exception {
        // Evita que uma chamada assincrona deixe o consumidor bloqueado indefinidamente.
        try { return future.get(20, java.util.concurrent.TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }
}
