package tests;

import patterns.ConstantPattern;
import rateLimiter.RateLimiter;
import rateLimiter.RateLimiterConfig;
import util.TimeUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class MultiThreads {

    public static void singleConstant() throws InterruptedException{
        // build a config: 10_000 tokens/sec for 1 second
        RateLimiterConfig.Builder config_builder = RateLimiterConfig.builder();
        long duration = 10;
        double tps = 1_000_000;
        config_builder
                .addSegment(ConstantPattern.of(tps), duration)
                .setMaxTokens(tps*10);
        RateLimiterConfig config = config_builder.build();

        // rate limiter with the above config
        RateLimiter limiter = new RateLimiter(config);
        long start = TimeUtil.milliTime();

        int numThreads = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicLong successCount = new AtomicLong();

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait until all threads are ready
                    while (TimeUtil.milliTime() <= start + duration*1000) {
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
