package com.bearnation.cardinal.bus;

public interface MessageBus extends AutoCloseable {
    void publish(String subject, String json);
    void subscribe(String subject, java.util.function.Consumer<String> handler);
    @Override void close();
}
