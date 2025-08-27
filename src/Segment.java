import patterns.Pattern;

public class Segment {
    private final Pattern pattern;
    private final long duration_millis;

    Segment(Pattern pattern, long duration_millis) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        if (duration_millis <= 0) {
            throw new IllegalArgumentException("duration_millis must be > 0");
        }
        this.pattern = pattern;
        this.duration_millis = duration_millis;
    }

    public long getDuration_millis() {
        return duration_millis;
    }

    public double integrate(long t0, long t1) {
        return pattern.integrate(t0, t1);
    }

    public double integrateFrom(long t0) {
        return pattern.integrate(t0, duration_millis);
    }

    public double integrateUpto(long t1) {
        return pattern.integrate(0, t1);
    }

    public double integrate() {
        return pattern.integrate(0, duration_millis);
    }
}
