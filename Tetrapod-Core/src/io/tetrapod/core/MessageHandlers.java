package io.tetrapod.core;

import io.tetrapod.core.rpc.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Thread safe message dispatcher class.
 */
public class MessageHandlers {
   
   private final Map<Long, List<SubscriptionAPI>> map = new ConcurrentHashMap<>();
   private final List<SubscriptionAPI> empty = new ArrayList<>();

   public List<SubscriptionAPI> get(int contractId, int structId) {
      List<SubscriptionAPI> list = map.get(makeKey(contractId, structId));
      return list == null ? empty : list;
   }
   
   public void add(Contract subscription, SubscriptionAPI handler) {
      for (Structure s : subscription.getMessages()) {
         add(s, handler);
      }
   }
   
   public void add(Structure s, SubscriptionAPI handler) {
      List<SubscriptionAPI> list = ensure(makeKey(s.getContractId(), s.getStructId()));
      list.add(handler);
   }
   
   private List<SubscriptionAPI> ensure(long key) {
      List<SubscriptionAPI> list = map.get(key);
      if (list == null) {
         list = new CopyOnWriteArrayList<>(); // good for concurrent reads >> writes
         map.put(key, list);
      }
      return list;
   }

   private long makeKey(int contractId, int structId) {
      return ((long) contractId << 32) | structId;
   }

}
