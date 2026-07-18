package patterns;

public interface Pattern {
    double integrate(long t0, long t1);

    String toString();
//    returns waiting time to acquire required tokens
    long getWaitingTime(long t0, double numTokens);
}
