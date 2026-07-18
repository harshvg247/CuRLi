package patterns;

//Interface:
//No Instantiation: You cannot create an object directly from an interface.
//Implicit Modifiers: Interface variables are automatically public static final (constants).
//Abstract Methods: By default, methods lack a body and are implicitly public abstract.
//No Constructors: Interfaces cannot contain constructor methods because they cannot be initialized directly.

public interface Pattern {
    double integrate(long t0, long t1);

    String toString();
//    returns waiting time to acquire required tokens
    long getWaitingTime(long t0, double numTokens);
}
