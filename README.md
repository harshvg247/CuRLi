# Custom Rate Limiter

[![Language](https://img.shields.io/badge/Language-Java-blue.svg)](https://www.java.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A sophisticated Java rate limiter that generates tokens based on a dynamic, multi-stage profile.

This is not a simple "token bucket." Instead of a single, constant rate, you can define a complex "load profile" with multiple segments, such as **ramping up**, **holding steady**, and **ramping down**. This makes it an ideal tool for realistic load testing and complex API quota simulation.

## Core Concept

This library works by defining a rate-generation function `r(t)` as a series of "pieces" (Segments).

1.  A **`Pattern`** defines a rate function over time (e.g., `ConstantPattern`, `LinearPattern`).
2.  A **`Segment`** applies a single `Pattern` for a specific `duration`.
3.  A **`RateLimiterConfig`** chains these `Segment`s together into a complete timeline.
4.  The **`RateLimiter`** generates new tokens by calculating the *integral* (the area under the curve) of this function between the last refill and the current time.


## Key Features

* **Dynamic Load Profiles:** Create complex rate-generation profiles by chaining segments.
* **Pattern-Based:** Ships with built-in patterns:
    * `ConstantPattern`: Generates tokens at a fixed rate (e.g., `100 tokens/sec`).
    * `LinearPattern`: Linearly ramps token generation from a `startRate` to an `endRate` over its duration.
* **Thread-Safe:** The `RateLimiter` class is fully thread-safe for use in concurrent applications.
* **Accurate:** Uses `System.nanoTime()` for monotonic time-keeping, preventing clock-skew issues.
* **Efficient:** The `RateLimiterConfig` pre-calculates segment start times for fast lookups.

---

## How to Use

Here is a complete example of creating a 3-stage load profile that ramps up, holds, and ramps down.

```java
import patterns.ConstantPattern;
import patterns.LinearPattern;
import rateLimiter.RateLimiter;
import rateLimiter.RateLimiterConfig;
import util.TimeUtil;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        // 1. Create a 3-stage configuration
        RateLimiterConfig config = RateLimiterConfig.builder()
                // Stage 1: Ramp up from 0 to 1000 tokens/sec over 10 seconds
                .addSegment(LinearPattern.of(0, 1000, 10), 10)
                
                // Stage 2: Hold at 1000 tokens/sec for 30 seconds
                .addSegment(ConstantPattern.of(1000), 30)
                
                // Stage 3: Ramp down from 1000 to 0 tokens/sec over 5 seconds
                .addSegment(LinearPattern.of(1000, 0, 5), 5)
                
                .setMaxTokens(5000) // Set a cap on accumulated tokens
                .build();

        // 2. Create the rate limiter
        RateLimiter limiter = new RateLimiter(config);

        // 3. Use in your application
        System.out.println("Running 40-second simulation...");
        long startTime = TimeUtil.milliTime();
        int requestCount = 0;

        while (TimeUtil.milliTime() - startTime < 40_000) {
            
            // tryAcquire() is hardcoded to 1000 tokens in this example
            if (limiter.tryAcquire()) {
                requestCount++;
                if (requestCount % 100 == 0) {
                    System.out.println("Acquired 100 requests...");
                }
            }
            
            // Sleep for a short time to simulate work
            Thread.sleep(10);
        }
        System.out.println("Simulation complete. Total requests acquired: " + requestCount);
    }
}
