import patterns.ConstantPattern;
import util.TimeUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // build a config: 10_000 tokens/sec for 1 second
        RateLimiterConfig.Builder config_builder = RateLimiterConfig.builder();
        config_builder.addSegment(ConstantPattern.of(10_000), 10)
                .addSegment(ConstantPattern.of(100), 5);
        RateLimiterConfig config = config_builder.build();

        // rate limiter with the above config
        RateLimiter limiter = new RateLimiter(config);
        long start = TimeUtil.milliTime();

        int numThreads = 10;
        int attemptsPerThread = 1480000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger();

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait until all threads are ready
                    while (successCount.longValue() < 100_500) {
                        if (limiter.tryAcquire()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads
        doneLatch.await();      // wait for all to finish
        long end = TimeUtil.milliTime();

        executor.shutdown();

        System.out.println("Total successes: " + successCount.get());
        System.out.println("Elapsed ms: " + (end - start));
    }
}
