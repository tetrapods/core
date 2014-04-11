package io.tetrapod.core.utils;

/**
 * A rate gauge that measures the rate of change in a value over time.
 */
public class Gauge {

   protected final long[] samples;
   protected int          cur;
   protected int          len;

   public Gauge(int numSamples) {
      samples = new long[numSamples];
   }

   public synchronized void sample(long value) {
      samples[cur] = value;
      cur = (cur + 1) % samples.length;
      len = Math.min(len + 1, samples.length);
   }

   public synchronized long getAverage() {
      long total = 0;
      for (int i = 0; i < len; i++) {
         total += samples[i];
      }
      return total / len;
   }

}
