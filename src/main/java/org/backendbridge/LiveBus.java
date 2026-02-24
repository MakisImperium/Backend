package org.backendbridge;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory event bus used for Admin UI live updates (SSE).
 */
public final class LiveBus {

    private LiveBus() {}

    private static final AtomicLong SUB_IDS = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, Subscriber> SUBS = new ConcurrentHashMap<>();

    public static Subscriber subscribe() {
        long id = SUB_IDS.getAndIncrement();
        Subscriber s = new Subscriber(id);
        SUBS.put(id, s);
        return s;
    }

    public static void unsubscribe(long id) {
        Subscriber s = SUBS.remove(id);
        if (s != null) s.close();
    }

    public static void publish(String eventName, String jsonPayload) {
        if (eventName == null || eventName.isBlank()) return;
        String payload = (jsonPayload == null) ? "{}" : jsonPayload;
        payload = payload.replace("\n", " ").replace("\r", " ");

        for (Subscriber s : SUBS.values()) {
            s.offer(new SseEvent(eventName, payload));
        }
    }

    public static void publishInvalidate(String... targets) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"targets\":[");
        if (targets != null) {
            boolean first = true;
            for (String t : targets) {
                if (t == null || t.isBlank()) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escJson(t.trim().toLowerCase())).append('"');
            }
        }
        sb.append("]}");
        publish("invalidate", sb.toString());
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class Subscriber implements AutoCloseable {
        private final long id;
        private final BlockingQueue<SseEvent> q = new LinkedBlockingQueue<>(500);
        private volatile boolean closed;

        private Subscriber(long id) { this.id = id; }

        public long id() { return id; }

        public void offer(SseEvent e) {
            if (closed) return;
            q.offer(e);
        }

        public SseEvent poll(long timeoutMillis) throws InterruptedException {
            if (closed) return null;
            return q.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override public void close() {
            closed = true;
            q.clear();
        }
    }

    public record SseEvent(String event, String dataJson) {}
}