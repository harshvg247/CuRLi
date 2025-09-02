package rateLimiter;

import patterns.Pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RateLimiterConfig {
    private final List<Segment> segments;
    private final List<Long> prefix;
    private final long totalDuration;
    private double maxTokens = 1_000_000;

    RateLimiterConfig(Builder builder) {
        // prevent operations like add/delete on list
        this.maxTokens = builder.max_tokens;
        this.segments = Collections.unmodifiableList(builder.segments);
        List<Long> prefix = new ArrayList<>();
        long totalDuration = 0;
        prefix.add(0L);
        for (Segment seg : segments) {
            prefix.add(prefix.getLast() + seg.getDuration_millis());
            totalDuration += seg.getDuration_millis();
        }
        this.prefix = Collections.unmodifiableList(prefix);
        this.totalDuration = totalDuration;
        System.out.println("prefix: " + prefix);
    }

    public static Builder builder() {
        return new Builder();
    }


    public double calculateTokens(long last_refill_millis, long now_millis) {
        if (last_refill_millis >= totalDuration) {
            return 0;
        }
        now_millis = Math.min(totalDuration, now_millis);
        if (last_refill_millis >= now_millis) return 0;

        long start = System.nanoTime();

        int start_idx = findSegLinear(last_refill_millis, false);
        int end_idx = findSegLinear(now_millis, true);

//        System.out.println((System.nanoTime() - start)/100);
        if (start_idx == end_idx) {
            return segments.get(start_idx).integrate(last_refill_millis - prefix.get(start_idx), now_millis - prefix.get(start_idx));
        }
        double token_cnt = 0;
        token_cnt += segments.get(start_idx).integrateFrom(last_refill_millis - prefix.get(start_idx));
        for (int ind = start_idx + 1; ind < end_idx; ind++) {
            token_cnt += segments.get(ind).integrate();
        }
        token_cnt += segments.get(end_idx).integrateUpto(now_millis - prefix.get(end_idx));
        return token_cnt;
    }

    private int findSeg(long t, boolean end) {
        int ind = Collections.binarySearch(prefix, t);
        if (ind >= 0) {
            if (end) return ind - 1;
            return ind;
        } else {
            return (-ind - 1) - 1;
        }
    }

//    better than binary search for segment.size < 100
    private int findSegLinear(long t, boolean end) {
        for (int i = 0; i < prefix.size(); i++) {
            if (prefix.get(i) > t) {
                return i - 1;
            }
        }
        // if t is beyond all prefix values, return last segment
        return prefix.size() - 2; // -2 because prefix has one extra element at start (0)
    }

    public double getMaxTokens() {
        return maxTokens;
    }

    public static class Builder {
        private final List<Segment> segments = new ArrayList<>();
        private double max_tokens = 1_000_000;

        public Builder addSegment(Pattern pattern, long duration_sec) {
            this.segments.add(new Segment(pattern, duration_sec * 1000));
            return this;
        }

        public Builder setMaxTokens(double max_tokens){
            this.max_tokens = max_tokens;
            return this;
        }

        public RateLimiterConfig build() {
            return new RateLimiterConfig(this);
        }
    }
}
