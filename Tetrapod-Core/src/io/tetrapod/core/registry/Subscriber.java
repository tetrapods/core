package io.tetrapod.core.registry;

/**
 * A reference counter for a client's topic subscription
 */
public class Subscriber {
   public final int id;
   public int       counter = 0;

   public Subscriber(int id) {
      this.id = id;
   }

   public String toString() {
      return "Subscriber-" + id;
   }
}
