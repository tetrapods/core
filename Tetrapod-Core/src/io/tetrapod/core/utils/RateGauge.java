package io.tetrapod.core.utils;

/**
 * A rate gauge that measures the rate of change in a value over time.
 */
public class RateGauge extends Gauge {

   private final long[] times;

   public RateGauge(int numSamples) {
      super(numSamples);
      times = new long[numSamples];
   }

   public synchronized void sample(long value) {
      times[cur] = System.nanoTime();
      super.sample(value);
   }

   public synchronized long getAveragePer(long millis) {
      if (len < 2) {
         return 0; // need at least two samples to get a rate
      }
      int first = len < samples.length ? 0 : (cur + 1) % samples.length;
      int last = cur == 0 ? samples.length - 1 : cur - 1;
      long delta = (samples[last] - samples[first]);
      long elapsed = Util.nanosToMillis(times[last] - times[first]);
      return Math.round(delta / (elapsed / (double) millis));
   }

   public synchronized long getAveragePerSecond() {
      return getAveragePer(1000);
   }
}
