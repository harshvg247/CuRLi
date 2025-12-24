package rateLimiter;

import util.TimeUtil;

public class RateLimiter {
    private final RateLimiterConfig config;
    private final long start_millis;
    private double avl_tokens;
    private long last_refill_millis;
    private final Object lock = new Object();
    private final double EPSILON = 1e-7;

    public RateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.avl_tokens = 0;
        this.last_refill_millis = TimeUtil.milliTime();
        this.start_millis = last_refill_millis;
    }

    public boolean tryAcquire(int num_tokens) {
        synchronized (lock) {
            if(avl_tokens >= num_tokens){
                avl_tokens-=num_tokens;
                return true;
            }
            refill();
            if (avl_tokens < num_tokens) return false;
            avl_tokens -= num_tokens;
            return true;
        }
    }

    public boolean tryAcquire(){
        return tryAcquire(1);
    }

    private void refill() {
        long now_millis = TimeUtil.milliTime();
        if(now_millis == last_refill_millis)return;
        avl_tokens += config.calculateTokens(last_refill_millis - start_millis, now_millis - start_millis);  // what if very close time??
        avl_tokens = Math.min(avl_tokens, config.getMaxTokens());
        if(Math.abs(Math.round(avl_tokens) - avl_tokens) < EPSILON){
            avl_tokens = Math.round(avl_tokens);
        }
        last_refill_millis = now_millis;
    }
}
