package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.tetrapod.protocol.core.*;

/**
 * Stores a buffer of named events and how long they took, to provide recent stats on performance.
 */
public class RequestStats {

   public final int bufferSize;

   public final Queue<ReqSample> requests = new ConcurrentLinkedQueue<>();

   private static final int GLOBAL_HISTOGRAM_BUCKETS = 120;

   public RequestStats() {
      this(1024 * 64);
   }

   public RequestStats(int bufferSize) {
      this.bufferSize = bufferSize;
   }

   public static class ReqSample {
      public final String key;
      public final long   timestamp;
      public final long   execution;

      public ReqSample(int fromEntityId, String key, long millis) {
         this.key = key;
         this.timestamp = System.currentTimeMillis();
         this.execution = millis;
      }
   }

   public void recordRequest(int fromEntityId, String query, long nanos) {
      requests.add(new ReqSample(fromEntityId, query, nanos));
      if (requests.size() > bufferSize) {
         requests.remove();
      }
   }

   // TODO: Add min/max/median/stddev....
   public ServiceRequestStatsResponse getRequestStats(int limit, long minTime, final RequestStatsSort sortBy) {
      final Map<String, Integer> counts = new HashMap<>();
      final Map<String, Long> execution = new HashMap<>();
      long minTimestamp = Long.MAX_VALUE;

      final int[] histogram = new int[GLOBAL_HISTOGRAM_BUCKETS];

      for (ReqSample sample : requests) {
         if (sample.timestamp >= minTime) {
            minTimestamp = Math.min(minTimestamp, sample.timestamp);

            // accumulate invocations count
            Integer count = counts.get(sample.key);
            if (count == null) {
               count = 1;
            } else {
               count = count + 1;
            }
            counts.put(sample.key, count);

            // accumulate microsecond totals
            Long time = execution.get(sample.key);
            if (time == null) {
               time = sample.execution / 1000;
            } else {
               time = time + sample.execution / 1000;
            }
            execution.put(sample.key, time);
         }
      }

      final long interval = System.currentTimeMillis() - minTimestamp;
      for (ReqSample sample : requests) {
         if (sample.timestamp >= minTime) {
            final int bucket = Math.round((sample.timestamp - minTimestamp) / interval);
            histogram[bucket]++;
         }
      }

      final List<String> sorted = new ArrayList<>(counts.keySet());
      Collections.sort(sorted, new Comparator<String>() {
         public int compare(String a, String b) {
            if (sortBy != null) {
               switch (sortBy) {
                  case COUNT:
                     if (counts.get(a) >= counts.get(b)) {
                        return -1;
                     } else {
                        return 1;
                     }
                  case TOTAL_TIME:
                     if (execution.get(a) >= execution.get(b)) {
                        return -1;
                     } else {
                        return 1;
                     }
                  case AVERAGE_TIME:
                     if (execution.get(a) / (double) counts.get(a) >= execution.get(b) / (double) counts.get(b)) {
                        return -1;
                     } else {
                        return 1;
                     }
               }
            }
            return a.compareTo(b);
         }
      });

      final List<RequestStat> stats = new ArrayList<RequestStat>();
      for (String key : sorted) {
         stats.add(new RequestStat(key, counts.get(key), execution.get(key), null, null));
         if (stats.size() >= limit)
            break;
      }

      return new ServiceRequestStatsResponse(stats, minTimestamp, null, histogram);
   }

}
