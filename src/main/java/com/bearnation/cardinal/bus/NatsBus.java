package com.bearnation.cardinal.bus;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

public final class NatsBus implements MessageBus {
    private final Connection nc;

    // url example: nats://user:pass@host:4222  (encode '$' in passwords as %24)
    public NatsBus(String url) throws Exception {
        this.nc = Nats.connect(url);
    }

    @Override
    public void publish(String subject, String json) {
        nc.publish(subject, json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void subscribe(String subject, Consumer<String> handler) {
        Dispatcher d = nc.createDispatcher(msg ->
                handler.accept(new String(msg.getData(), StandardCharsets.UTF_8))
        );
        d.subscribe(subject);
    }

    @Override
    public void close() {
        try { nc.flush(Duration.ofSeconds(2)); } catch (Exception ignored) {}
        try { nc.close(); } catch (Exception ignored) {}
    }
}
