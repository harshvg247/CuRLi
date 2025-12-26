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
}
