package io.tetrapod.core.utils;

/**
 * A rate gauge that keeps a history of event times
 */
public class RateLimiter {

   protected final long[] samples;
   protected int          cur = -1;
   protected int          len;
   protected int          perMillis;
   
   // If we're currently limiting, how many have been ignored during this period of limiting.
   protected int          ignoredCount;

   public RateLimiter(int max, int perMillis) {
      this.samples = new long[max];
      this.perMillis = perMillis;
      this.ignoredCount = 0;
   }

   public synchronized void mark() {
      cur = (cur + 1) % samples.length;
      samples[cur] = System.currentTimeMillis();
      len = Math.min(len + 1, samples.length);
   }

   public synchronized Long getLastValue() {
      if (len > 0) {
         return samples[cur];
      } else {
         return null;
      }
   }

   public synchronized Long getOldestValue() {
      if (len > 0) {
         if (len < samples.length) {
            return samples[0];
         } else {
            return samples[(cur + 1) % samples.length];
         }
      } else {
         return null;
      }
   }

   public synchronized int getLength() {
      return len;
   }

   public synchronized boolean shouldLimit() {
      if (len >= samples.length) {
         if ((System.currentTimeMillis() - getOldestValue()) < perMillis) {
            ignoredCount++;
            return true;
         } else {
            ignoredCount = 0;
            return false;            
         }
      } else {
         ignoredCount = 0;
         return false;
      }
   }
   
   public synchronized int getIgnoreCount() {
      return ignoredCount;
   }

}
