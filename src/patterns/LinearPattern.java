package patterns;

//Slope Precision Isolation:
// It handles small/flat line exceptions where slopes are virtually zero ($< 10^{-9}$),
// dropping back smoothly into constant calculations instead of running into division-by-zero (NaN)
// runtime blocks.

public class LinearPattern implements Pattern {

    private final double startRate;
    private final double endRate;
    private final double slope;
    private final long duration;

    /**
     * Constructs a LinearPattern profile.
     * @param startRate      The token generation rate at the beginning of the segment (tokens/sec).
     * @param endRate        The token generation rate at the end of the segment (tokens/sec).
     * @param durationSec    The total active lifespan of this segment in seconds.
     */
    LinearPattern(double startRate, double endRate, long durationSec) {
        if (startRate < 0 || endRate < 0 || durationSec <= 0) {
            throw new IllegalArgumentException("Rates must be >= 0 and duration must be > 0");
        }
        this.startRate = startRate;
        this.endRate = endRate;
        // Convert internal duration keeping to milliseconds to align with RateLimiterConfig calculations
        this.duration = durationSec * 1000;
        // Slope calculated per millisecond unit time
        this.slope = (endRate - startRate) / (double) this.duration;
    }

    public static LinearPattern of(double startRate, double endRate, long durationSec) {
        return new LinearPattern(startRate, endRate, durationSec);
    }

    @Override
    public double integrate(long t0, long t1) {
        if (t0 > t1) {
            throw new IllegalArgumentException("t0 must be <= t1");
        }

        // Clamp boundaries strictly within [0, duration] bounds to ensure precision safety
        long a = Math.max(0, Math.min(t0, duration));
        long b = Math.max(0, Math.min(t1, duration));

        if (a == b) {
            return 0;
        }

        // Analytical calculus integral evaluation: r0 * delta_t + 0.5 * slope * (t1^2 - t0^2)
        double integralPart = (startRate * (b - a)) + 0.5 * slope * ((double) b * b - (double) a * a);

        return Math.max(integralPart, 0);
    }

    @Override
    public long getWaitingTime(long t0, double numTokens) {
        if (numTokens <= 0) {
            // Immediate availability if no tokens are needed
            return 0;
        }

        // Clamp the starting time boundary to the segment timeline
        long a = Math.max(0, Math.min(t0, duration));

        // Dynamic rate at our current timeline mark 'a'
        double currentRate = startRate + slope * a;

        /* * Solve quadratic equations derived from integration criteria:
         * tokensNeeded = currentRate * delta_t + 0.5 * slope * delta_t^2
         * * Rewritten standard form: (0.5 * slope) * dt^2 + (currentRate) * dt - numTokens = 0
         */
        if (Math.abs(slope) < 1e-9) {
            // Degenerate edge case: If slope is roughly zero, handle it as a constant rate pattern safely
            return currentRate > 0 ? (long) (numTokens / currentRate) : Long.MAX_VALUE;
        }

        // Quadratic coefficients: A*x^2 + B*x + C = 0
        double A = 0.5 * slope;
        double B = currentRate;
        double C = -numTokens;

        double discriminant = (B * B) - (4.0 * A * C);
        if (discriminant < 0) {
            // Mathematical impossibility or outside real-number bounds
            return Long.MAX_VALUE;
        }

        // Quadratic standard equation: (-B + sqrt(discriminant)) / (2 * A)
        double root = (-B + Math.sqrt(discriminant)) / (2.0 * A);

        // Falling edge handling or negative timeline corrections fallback
        if (root < 0) {
            root = (-B - Math.sqrt(discriminant)) / (2.0 * A);
        }

        return root > 0 ? (long) Math.ceil(root) : Long.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "LinearPattern: startRate = " + startRate +
                ", endRate = " + endRate +
                ", duration = " + (duration / 1000) + "s";
    }
}