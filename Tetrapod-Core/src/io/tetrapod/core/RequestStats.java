package io.tetrapod.core;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.tetrapod.core.utils.Util;
import io.tetrapod.protocol.core.*;

/**
 * Stores a buffer of named events and how long they took, to provide recent stats on performance.
 */
public class RequestStats {

   public final int              bufferSize;

   public final Queue<ReqSample> requests                 = new ConcurrentLinkedQueue<>();

   private static final int      GLOBAL_HISTOGRAM_BUCKETS = 120;

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
      public final int    result;
      public final int    fromEntityId;

      public ReqSample(int fromEntityId, String key, long millis, int result) {
         this.fromEntityId = fromEntityId;
         this.key = key;
         this.timestamp = System.currentTimeMillis();
         this.execution = millis;
         this.result = result;
      }
   }

   public void recordRequest(int fromEntityId, String query, long nanos, int result) {
      requests.add(new ReqSample(fromEntityId, query, nanos, result));
      if (requests.size() > bufferSize) {
         requests.remove();
      }
   }

   // TODO: Add min/max/median/stddev....
   public ServiceRequestStatsResponse getRequestStats(int limit, long minTime, final RequestStatsSort sortBy) {
      final long now = System.currentTimeMillis();
      final Map<String, Integer> counts = new HashMap<>();
      final Map<String, Long> execution = new HashMap<>();
      final Map<String, Map<Integer, Integer>> entities = new HashMap<>();
      final Map<String, Map<Integer, Integer>> errors = new HashMap<>();
      //final Map<String, Integer[]> timelines = new HashMap<>();
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

            Map<Integer, Integer> entityMap = entities.get(sample.key);
            if (entityMap == null) {
               entityMap = new HashMap<>();
               entities.put(sample.key, entityMap);
            }
            entityMap.putIfAbsent(sample.fromEntityId, 0);
            entityMap.put(sample.fromEntityId, entityMap.get(sample.fromEntityId) + 1);

            Map<Integer, Integer> errorMap = errors.get(sample.key);
            if (errorMap == null) {
               errorMap = new HashMap<>();
               errors.put(sample.key, errorMap);
            }
            errorMap.putIfAbsent(sample.result, 0);
            errorMap.put(sample.result, errorMap.get(sample.result) + 1);
         }
      }

      final long interval = (now - minTimestamp) / histogram.length;
      for (ReqSample sample : requests) {
         if (sample.timestamp >= minTime && sample.timestamp <= now) {
            final long delta = sample.timestamp - minTimestamp;
            final int bucket = Math.min((int) (delta / interval), histogram.length - 1);

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
         final int[] entitiesArr = Util.toIntArray(topN(entities.get(key), (a, b) -> {
            return a.compareTo(b);
         } , 10));
         final int[] errorsArr = Util.toIntArray(topN(errors.get(key), (a, b) -> {
            return a.compareTo(b);
         } , 10));
         stats.add(new RequestStat(key, counts.get(key), execution.get(key), entitiesArr, errorsArr, null));
         if (stats.size() >= limit)
            break;
      }

      return new ServiceRequestStatsResponse(stats, minTimestamp, null, histogram, now);
   }

   public static <T, V extends Comparable<V>> List<T> topN(Map<T, V> map, Comparator<V> comp, int n) {
      final List<T> res = new ArrayList<>();
      final List<T> sorted = new ArrayList<>(map.keySet());
      Collections.sort(sorted, (a, b) -> {
         return map.get(a).compareTo(map.get(b));
      });
      for (T t : sorted) {
         res.add(t);
         if (res.size() >= n)
            break;
      }
      return res;
   }

   public double getErrorRate() {
      double errors = 0;
      double total = 0;
      final long now = System.currentTimeMillis();
      for (ReqSample sample : requests) {
         if (sample.timestamp > now - Util.ONE_MINUTE) {
            if (sample.result > 0) {
               errors++;
            }
            total++;
         }
      }
      return errors / total;
   }

}
