package com.enterprise.product.modules.stock.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory bounded queue + configurable thread pool for async DB sync of stock updates.
 *
 * Parallelism is controlled by `stock.worker.parallelism`.
 * Queue capacity is controlled by `stock.worker.queue-capacity`.
 *
 * If the queue is full, enqueue blocks briefly; on failure the caller should surface
 * a retryable error to the client (Redis stock was NOT mutated yet at this point — the
 * reservation service is expected to call this before committing Redis).
 *
 * NOTE: For extreme scale, swap this for a persistent broker (Kafka topic
 * `product.stock.update`). The consumer side already has a template below.
 */
@Component
@Slf4j
public class StockUpdateWorkerQueue {

    @Value("${stock.worker.parallelism:8}")
    private int parallelism;

    @Value("${stock.worker.queue-capacity:10000}")
    private int queueCapacity;

    @Value("${stock.worker.enqueue-timeout-ms:2000}")
    private long enqueueTimeoutMs;

    private BlockingQueue<StockUpdateTask> queue;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private StockUpdateWorker worker;

    public StockUpdateWorkerQueue(StockUpdateWorker worker) {
        this.worker = worker;
    }

    @PostConstruct
    void start() {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r);
            t.setName("stock-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < parallelism; i++) {
            executor.submit(this::loop);
        }
        log.info("StockUpdateWorkerQueue started: parallelism={} queueCapacity={}", parallelism, queueCapacity);
    }

    @PreDestroy
    void stop() {
        running.set(false);
        executor.shutdownNow();
        log.info("StockUpdateWorkerQueue stopped");
    }

    public void enqueue(StockUpdateTask task) {
        try {
            boolean offered = queue.offer(task, enqueueTimeoutMs, TimeUnit.MILLISECONDS);
            if (!offered) {
                throw new IllegalStateException("Stock update queue saturated (capacity=" + queueCapacity + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueuing stock update", e);
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                StockUpdateTask task = queue.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                worker.process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("Stock worker loop error", ex);
            }
        }
    }

    public int queueDepth() { return queue == null ? 0 : queue.size(); }
    public int parallelism() { return parallelism; }
}
