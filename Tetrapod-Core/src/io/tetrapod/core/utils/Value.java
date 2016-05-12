package io.tetrapod.core.utils;

public class Value<T> {
   T       value;
   boolean set = false;

   public Value(T initial) {
      this.value = initial;
   }

   public Value() {
      this(null);
   }

   public synchronized T get() {
      return value;
   }

   public synchronized void set(T value) {
      this.value = value;
      this.set = true;
      notifyAll();
   }

   public T waitForValue() {
      while (!set) {
         synchronized (this) {
            try {
               wait(100);
            } catch (InterruptedException e) {}
         }
      }
      return value;
   }

   public void reset() {
      set = false;
   }

   public boolean isSet() {
      return set;
   }

   public void setSet(boolean set) {
      this.set = set;
   }
}
