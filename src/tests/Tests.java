package tests;

import patterns.ConstantPattern;
import rateLimiter.RateLimiter;
import rateLimiter.RateLimiterConfig;
import util.TimeUtil;

import javax.crypto.spec.PSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Tests {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Starting CuRLi Verification Test Suite ---\n");

        runDeterministicConstantTest();
        runSegmentTransitionTest();
        runHighThroughputConcurrencyTest();
        runPostTimelineExpiryTest();

        System.out.println("\n--- All Tests Execution Complete ---");
    }

    /**
     * Pillar A: Validates exact math calculation for a simple ConstantPattern.
     */
    private static void runDeterministicConstantTest() throws InterruptedException {
        System.out.print("Test 1: Deterministic Constant Rate Math... ");

        // 10,000 tokens/sec = 10 tokens/ms
        RateLimiterConfig config = RateLimiterConfig.builder()
                .addSegment(ConstantPattern.of(10000), 2)
                .setMaxTokens(50000)
                .build();

        RateLimiter limiter = new RateLimiter(config);

        // Wait exactly 200 milliseconds to let tokens generate
        long x = System.nanoTime();
        while(System.nanoTime() - x < 200000000){

        }
//        Thread.sleep(200);
//        System.out.println(System.nanoTime() - x);
        // 200ms * 10 tokens/ms = ~2000 tokens generated
        int acquired = 0;
        while (limiter.tryAcquire(10)) {
            acquired+=10;
        }

        // Allow a small padding window for execution delays during Thread.sleep()
        if (acquired >= 1900 && acquired <= 2100) {
            System.out.println("PASSED (Expected: 2000, Acquired: " + acquired + " tokens)");
        } else {
            System.out.println("FAILED (Expected ~2000, but got: " + acquired + ")");
        }
    }

    /**
     * Pillar A: Validates token calculation across a multi-stage profile boundary.
     */
    private static void runSegmentTransitionTest() throws InterruptedException {
        System.out.print("Test 2: Multi-Segment Transition Integration... ");

        // Segment 1: 5000 tokens/sec for 1 second (5 tokens/ms)
        // Segment 2: 10000 tokens/sec for 1 second (10 tokens/ms)
        RateLimiterConfig config = RateLimiterConfig.builder()
                .addSegment(ConstantPattern.of(5000), 1)
                .addSegment(ConstantPattern.of(10000), 1)
                .setMaxTokens(20000)
                .build();

        RateLimiter limiter = new RateLimiter(config);

        // Sleep for 1200ms (crosses over into the second segment)
        // Expected: 1000ms * 5 tokens/ms + 200ms * 10 tokens/ms = 5000 + 2000 = 7000 tokens
        long x = System.nanoTime();
        while(System.nanoTime() - x < 1200000000){

        }

        int acquired = 0;
        while (limiter.tryAcquire(10)) {
            acquired+=10;
        }

        if (acquired >= 6800 && acquired <= 7300) {
            System.out.println("PASSED (Expected: 7000, Acquired: " + acquired + " tokens)");
        } else {
            System.out.println("FAILED (Expected ~7000, but got: " + acquired + ")");
        }
    }

    /**
     * Pillar B: Verifies thread safety and token counts under high race conditions.
     */
    private static void runHighThroughputConcurrencyTest() throws InterruptedException {
        System.out.print("Test 3: High-Throughput Concurrency Safety... ");

        long durationSec = 2;
        double tps = 50000;

        RateLimiterConfig config = RateLimiterConfig.builder()
                .addSegment(ConstantPattern.of(tps), durationSec)
                .setMaxTokens(tps * durationSec)
                .build();

        RateLimiter limiter = new RateLimiter(config);
        long start = TimeUtil.milliTime();

        int numThreads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicLong totalSuccessAcquisitions = new AtomicLong();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (TimeUtil.milliTime() < start + (durationSec * 1000)) {
                        if (limiter.tryAcquire()) {
                            totalSuccessAcquisitions.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Max possible generation is tps * durationSec = 100,000 tokens
        long maxAllowedTokens = (long) (tps * durationSec);
        long actualAcquired = totalSuccessAcquisitions.get();

        // Safety check: Concurrency must NEVER exceed theoretical max limits plus execution jitter
        if (actualAcquired <= maxAllowedTokens + 500) {
            System.out.println("PASSED (Acquired: " + actualAcquired + " / Max Limit: " + maxAllowedTokens + ")");
        } else {
            System.out.println("FAILED (Over-allocation detected! Acquired: " + actualAcquired + " > Max Limit: " + maxAllowedTokens + ")");
        }
    }

    /**
     * Pillar C: Verifies behavior after all timeline profiles are completely exhausted.
     */
    private static void runPostTimelineExpiryTest() throws InterruptedException {
        System.out.print("Test 4: Expiry Boundary Condition Handling... ");

        // Very brief profile: 100000 tokens/sec for only 0.1 seconds (100ms duration)
        RateLimiterConfig config = RateLimiterConfig.builder()
                .addSegment(ConstantPattern.of(100000), 1) // 1 second timeline
                .build();

        RateLimiter limiter = new RateLimiter(config);

        // Sleep for 1500ms so the entire profile config expires completely
        Thread.sleep(1500);

        // Clear out all tokens generated while it was active
        while (limiter.tryAcquire()) {
            // drain
        }

        // Attempting to acquire tokens post-timeline expiration must instantly return false
        boolean acquiredPostExpiry = limiter.tryAcquire();

        if (!acquiredPostExpiry) {
            System.out.println("PASSED (Zero token generation after timeline exhaustion)");
        } else {
            System.out.println("FAILED (Generated tokens even after total configuration duration ended)");
        }
    }
}