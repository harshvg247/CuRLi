package patterns;

public class LinearPattern implements Pattern {
    private final double startRate;
    private final double endRate;
    private final long duration;

    LinearPattern(double startRate, double endRate, long durationSec) {
        if (startRate < 0 || endRate < 0 || durationSec < 0) {
            throw new IllegalArgumentException("start, endRate and duration must be >= 0");
        }
        this.startRate = startRate;
        this.endRate = endRate;
        this.duration = durationSec * 1000;
    }

    public static LinearPattern of(double startRate, double endRate, long durationSec) {
        return new LinearPattern(startRate, endRate, durationSec);
    }

    public double integrate(long t0, long t1) {
        if (t0 > t1) {
            throw new IllegalArgumentException("t0 must be <= t1");
        }

        // clamp times to [0, duration]
        long a = Math.max(0, Math.min(t0, duration));
        long b = Math.max(0, Math.min(t1, duration));

        // integral of a linear rate function: r(t) = start + slope * t
        double slope = (endRate - startRate) / duration;

        double integralPart = (startRate * (b - a)) + 0.5 * slope * (b * b - a * a);

        // if t1 exceeds duration, add constant part with endRate
        if (t1 > duration) {
            integralPart += endRate * (t1 - Math.max(t0, duration));
        }
        return Math.max(integralPart, 0);
    }
    @Override
    public String toString() {
        return "LinearPattern: startRate = " + startRate +
                ", endRate = " + endRate +
                ", duration = " + duration + "s";
    }
}
