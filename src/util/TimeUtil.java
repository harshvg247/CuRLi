package util;

public class TimeUtil {
    public static long milliTime() {
        return System.nanoTime() / 1_000_000;
    }
}
