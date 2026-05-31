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
        while (true) {
            if (tryAcquire(requestedTokens)) {
                return;
            }

            LimiterState current = state.get();
            long now = TimeUtil.milliTime();
            double currentTokens = current.avl_tokens();

            if (now > current.last_refill_millis()) {
                double generated = config.calculateTokens(
                        current.last_refill_millis() - start_millis,
                        now - start_millis
                );
                currentTokens = Math.min(config.getMaxTokens(), currentTokens + generated);
            }

            double missingTokens = requestedTokens - currentTokens;
            if (missingTokens <= 0) {
                continue;
            }

            long currentRelativeTime = now - start_millis;
            long waitMillis = config.calculateDelayMillis(currentRelativeTime, missingTokens);

            if (waitMillis == Long.MAX_VALUE) {
                // Timeline expired scenario: sleep fallback to preserve CPU bounds
                Thread.sleep(10);
                continue;
            }

            if (waitMillis > 0) {
                    Thread.sleep(waitMillis);
            } else {
//                The Thread.yield() method in Java is a static native method
//                used to hint to the thread scheduler that the currently
//                executing thread is willing to pause its execution
//                and give up its current processor time slice
                Thread.yield();
            }
        }
    }

    public void acquire() throws InterruptedException{
        acquire(1);
    }
}
