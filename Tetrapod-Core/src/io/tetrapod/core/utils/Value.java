package io.tetrapod.core.utils;

public class Value<T> {
   T value;
   
   public Value(T initial) {
      this.value = initial;
   }
   
   public Value() {
      this(null);
   }
   
   public T get() {
      return value;
   }
   
   public void set(T value) {
      this.value = value;
   }
}
