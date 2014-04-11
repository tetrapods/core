package io.tetrapod.core.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A high-performance counter that allows for reduced contention when lots of threads need to increment, but there are relatively few reads
 */
public class Accumulator {

   // TODO:  Actually implement. For now, jsut wrapping an AtomicLong
   // OPTIMIZE: use a collection of counters and threadlocal storage to reduce contention

   private AtomicLong value = new AtomicLong();

   public void increment() {
      value.incrementAndGet();
   }

   public long get() {
      return value.get();
   }

}
