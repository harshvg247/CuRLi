package rateLimiter;

import util.TimeUtil;

import java.util.concurrent.atomic.AtomicReference;

public class RateLimiter {
    private final RateLimiterConfig config;
    private final long start_millis;
    private final double EPSILON = 1e-7;
    private final AtomicReference<LimiterState> state;

    public RateLimiter(RateLimiterConfig config) {
        this.config = config;
        long now = TimeUtil.milliTime();
        this.start_millis = now;
        this.state = new AtomicReference<>(new LimiterState(0, now));
    }

    public long getStart_millis(){
        return start_millis;
    }

    public double getNumTokens(){
        return state.get().avl_tokens();
    }

    public boolean tryAcquire(double requestedTokens) {
        while (true) { // The CAS Loop
            LimiterState current = state.get();
            long now = TimeUtil.milliTime();
            // 1. Calculate how many tokens to add since last check
            double newTokens = current.avl_tokens();
            if (now > current.last_refill_millis()) {
                double generated = config.calculateTokens(
                        current.last_refill_millis() - start_millis,
                        now - start_millis
                );
                newTokens = Math.min(config.getMaxTokens(), newTokens + generated);
                if(Math.abs(Math.round(newTokens) - newTokens) < EPSILON){
                    newTokens = Math.round(newTokens);
                }
            }
            // 2. Check if we have enough
            if (newTokens < requestedTokens) {
                return false;
            }

            // 3. Create the potential new state
            LimiterState next = new LimiterState(newTokens - requestedTokens, now);

            // 4. Try to commit the change.
            // If another thread changed 'state' in the meantime, this returns false.
            if (state.compareAndSet(current, next)) {
                return true;
            }
            // If we failed, the loop runs again with the updated 'current' state.
        }
    }

    public boolean tryAcquire(){
        return tryAcquire(1);
    }

//    private void refill() {
//        long now_millis = TimeUtil.milliTime();
//        if(now_millis == last_refill_millis)return;
//        avl_tokens += config.calculateTokens(last_refill_millis - start_millis, now_millis - start_millis);  // what if very close time??
//        avl_tokens = Math.min(avl_tokens, config.getMaxTokens());
//        if(Math.abs(Math.round(avl_tokens) - avl_tokens) < EPSILON){
//            avl_tokens = Math.round(avl_tokens);
//        }
//        last_refill_millis = now_millis;
//    }

    public void acquire(double requestedTokens) throws InterruptedException {
        // Fast-fail if the request exceeds maximum bucket capacity, preventing impossible acquisitions
        if (requestedTokens > config.getMaxTokens()) {
            throw new IllegalArgumentException("Requested tokens exceed maximum bucket capacity.");
        }

        long waitMillis = reserve(requestedTokens);

        // Fixes the infinite Thread.sleep(10) livelock bug
        if (waitMillis == Long.MAX_VALUE) {
            throw new IllegalStateException("Rate limit timeline expired. Cannot acquire requested tokens.");
        }

        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    /**
     * Atomically calculates and reserves future tokens, returning the required sleep time.
     */
    private long reserve(double requestedTokens) {
        while (true) {
            LimiterState current = state.get();
            long now = TimeUtil.milliTime();

            double currentTokens = current.avl_tokens();
            long lastRefill = current.last_refill_millis();

            // 1. Calculate the actual tokens available at 'now'
            if (now > lastRefill) {
                double generated = config.calculateTokens(
                        lastRefill - start_millis,
                        now - start_millis
                );
                currentTokens = Math.min(config.getMaxTokens(), currentTokens + generated);

                // Clean up floating point precision drift
                if (Math.abs(Math.round(currentTokens) - currentTokens) < EPSILON) {
                    currentTokens = Math.round(currentTokens);
                }
            }

            // 2. If we have enough tokens, consume them immediately without waiting
            if (currentTokens >= requestedTokens) {
                long newRefillTime = Math.max(now, lastRefill);
                LimiterState next = new LimiterState(currentTokens - requestedTokens, newRefillTime);

                if (state.compareAndSet(current, next)) {
                    return 0L; // No wait time required
                }
                continue; // CAS failed due to contention; loop to retry
            }

            // 3. We don't have enough tokens; calculate future reservation time
            double missingTokens = requestedTokens - currentTokens;

            // Start projecting time from the furthest point in the future
            long timeToRefillFrom = Math.max(now, lastRefill);
            long currentRelativeTime = timeToRefillFrom - start_millis;

            long delayMillis = config.calculateDelayMillis(currentRelativeTime, missingTokens);

            // If the timeline expires before fulfilling the missing tokens, return the MAX_VALUE marker
            if (delayMillis == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }

            // 4. Reserve the tokens by advancing the refill timeline
            long claimTime = timeToRefillFrom + delayMillis;

            // PREVENTS CUMULATIVE DRIFT:
            // Because calculateDelayMillis uses Math.ceil(), 'claimTime' rounds up to the next whole millisecond.
            // We calculate the exact tokens generated during this rounded delay to preserve the fractional difference.
            double exactGenerated = config.calculateTokens(
                    currentRelativeTime,
                    claimTime - start_millis
            );

            double leftoverTokens = exactGenerated - missingTokens;

            if (Math.abs(Math.round(leftoverTokens) - leftoverTokens) < EPSILON) {
                leftoverTokens = Math.round(leftoverTokens);
            }
            leftoverTokens = Math.max(0, leftoverTokens); // Ensure we never drop below 0 due to precision drift

            // 5. Commit the reservation with the exact fractional leftover carrying forward
            LimiterState next = new LimiterState(leftoverTokens, claimTime);

            if (state.compareAndSet(current, next)) {
                // Return the actual sleep duration relative to the current physical time
                return claimTime - now;
            }
        }
    }

//    public void acquire(double requestedTokens) throws InterruptedException, IllegalStateException {
//        while (true) {
//            if (tryAcquire(requestedTokens)) {
//                return;
//            }
//
//            LimiterState current = state.get();
//            long now = TimeUtil.milliTime();
//            double currentTokens = current.avl_tokens();
//
//            if (now > current.last_refill_millis()) {
//                double generated = config.calculateTokens(
//                        current.last_refill_millis() - start_millis,
//                        now - start_millis
//                );
//                currentTokens = Math.min(config.getMaxTokens(), currentTokens + generated);
//            }
//
//            double missingTokens = requestedTokens - currentTokens;
//            if (missingTokens <= 0) {
//                Thread.yield();
//
////              This instantly sends the thread back to the top of the while (true) loop without any Thread.yield() or backoff.
////              Under heavy concurrent load, multiple threads will furiously spin in this tight loop,
////              burning CPU cycles without making progress.
//                continue;
//            }
//
//            long currentRelativeTime = now - start_millis;
//            long waitMillis = config.calculateDelayMillis(currentRelativeTime, missingTokens);
//
//            if (waitMillis == Long.MAX_VALUE) {
//                // Timeline expired scenario: sleep fallback to preserve CPU bounds
//                throw new IllegalStateException("Required token amount exceeds maximum possible tokens");
//            }
//
//            if (waitMillis > 0) {
//                    Thread.sleep(waitMillis);
//            } else {
////                The Thread.yield() method in Java is a static native method
////                used to hint to the thread scheduler that the currently
////                executing thread is willing to pause its execution
////                and give up its current processor time slice
//                Thread.yield();
//            }
//        }
//    }

    public void acquire() throws InterruptedException{
        acquire(1);
    }
}
