package patterns;

public class ConstantPattern implements Pattern {

    private final double rate;

    ConstantPattern(double r) {
        if (r <= 0) {
            throw new IllegalArgumentException("rate must be > 0");
        }
        rate = r;
    }

    public static ConstantPattern of(double r) {

        return new ConstantPattern(r);
    }


    @Override
    public double integrate(long t0, long t1) {
        if (t0 > t1) {
            throw new IllegalArgumentException("t0 must be <= t1");
        }
//        clamp to avoid floating-point negatives
        return Math.max(rate * (t1 - t0), 0);
    }

    @Override
    public String toString() {
        return "ConstantPattern: r = " + rate;
    }
}
