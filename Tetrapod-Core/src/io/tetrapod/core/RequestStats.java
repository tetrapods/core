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

   private static final int      HISTOGRAM_BUCKETS = 100;

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
      final Map<String, int[]> timelines = new HashMap<>();
      long minTimestamp = now;

      final int[] histogram = new int[HISTOGRAM_BUCKETS];

      for (ReqSample sample : requests) {
         if (sample.timestamp >= minTime) {
            minTimestamp = Math.min(minTimestamp, sample.timestamp);

            // accumulate invocations count
            Integer count = Util.getOrMake(counts, sample.key, () -> {
               return 0;
            });
            counts.put(sample.key, count + 1);

            // accumulate microsecond totals
            Long time = Util.getOrMake(execution, sample.key, () -> {
               return 0L;
            });
            execution.put(sample.key, time + sample.execution / 1000);

            // most common request entities
            Map<Integer, Integer> entityMap = Util.getOrMake(entities, sample.key, () -> {
               return new HashMap<>();
            });
            entityMap.putIfAbsent(sample.fromEntityId, 0);
            entityMap.put(sample.fromEntityId, entityMap.get(sample.fromEntityId) + 1);

            // most common results
            Map<Integer, Integer> errorMap = Util.getOrMake(errors, sample.key, () -> {
               return new HashMap<>();
            });
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

            int[] hist = Util.getOrMake(timelines, sample.key, () -> {
               return new int[HISTOGRAM_BUCKETS];
            }); 
            hist[bucket]++;

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
         final StatPair[] entitiesArr = toStatPair(entities.get(key), topN(entities.get(key), (a, b) -> {
            return a.compareTo(b);
         } , 10));
         final StatPair[] errorsArr = toStatPair(errors.get(key), topN(errors.get(key), (a, b) -> {
            return a.compareTo(b);
         } , 10));

         stats.add(new RequestStat(key, counts.get(key), execution.get(key), entitiesArr, errorsArr, timelines.get(key)));
         if (stats.size() >= limit)
            break;
      }

      return new ServiceRequestStatsResponse(stats, minTimestamp, null, histogram, now);
   }

   private StatPair[] toStatPair(Map<Integer, Integer> map, List<Integer> top) {
      StatPair[] items = new StatPair[top.size()];
      int i = 0;
      for (Integer key : top) {
         items[i++] = new StatPair(key, map.get(key));
      }
      return items;
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
