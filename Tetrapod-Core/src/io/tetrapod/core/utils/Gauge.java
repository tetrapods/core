package io.tetrapod.core.utils;

/**
 * A rate gauge that keeps a history of sampled values
 */
public class Gauge {

   protected final long[] samples;
   protected int          cur = -1;
   protected int          len;

   public Gauge(int numSamples) {
      samples = new long[numSamples];
   }

   public synchronized void sample(long value) {
      cur = (cur + 1) % samples.length;
      samples[cur] = value;
      len = Math.min(len + 1, samples.length);
   }

   public synchronized long getLastValue() {
      if (len > 0) {
         return samples[cur];
      } else {
         return 0;
      }
   }

   public synchronized long getAverage() {
      long total = 0;
      for (int i = 0; i < len; i++) {
         total += samples[i];
      }
      return total / len;
   }

}
