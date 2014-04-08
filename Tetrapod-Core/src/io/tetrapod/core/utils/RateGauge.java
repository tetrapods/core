package io.tetrapod.core.utils;

/**
 * A rate gauge that measures the rate of change in a value over time.
 */
public class RateGauge {

   public static class Sample {
      long value;
      long time;
   }

   private final Sample[] samples;
   private int            cur;
   private int            len;

   public RateGauge(int numSamples) {
      samples = new Sample[numSamples];
      for (int i = 0; i < numSamples; i++) {
         samples[i] = new Sample();
      }
   }

   public synchronized void sample(long value) {
      samples[cur].value = value;
      samples[cur].time = System.nanoTime();
      cur = (cur + 1) % samples.length;
      len = Math.min(len + 1, samples.length);
   }

   public synchronized long getAverage() {
      long total = 0;
      for (int i = 0; i < len; i++) {
         total += samples[i].value;
      }
      return total / len;
   }

   public synchronized long getAveragePer(long millis) {
      if (len < 2) {
         return 0; // need at least two samples to get a rate
      }
      int first = len < samples.length ? 0 : (cur + 1) % samples.length;
      int last = cur == 0 ? samples.length - 1 : cur - 1;
      long delta = (samples[last].value - samples[first].value);
      long elapsed = (samples[last].time - samples[first].time) / 1000000L;
      return Math.round(delta / (elapsed / (double) millis));
   }

   public synchronized long getAveragePerSecond() {
      return getAveragePer(1000);
   }
}
