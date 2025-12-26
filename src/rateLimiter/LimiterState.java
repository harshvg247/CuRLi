package rateLimiter;

public record LimiterState(double avl_tokens, long last_refill_millis) {
}
