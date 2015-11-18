package io.tetrapod.core.rpc;

public class Flags_long<T> {

   public long value;
   
   final public boolean hasAny(long flags) {
      return (value & flags) != 0;
   }

   final public boolean isSet(long flags) {
      return (value & flags) == flags;
   }

   final public boolean isNoneSet(long flags) {
      return (value & flags) == 0;
   }
   
   @SuppressWarnings("unchecked")
   public T set(long flags) {
      value |= flags;
      return (T)this;
   }
   
   @SuppressWarnings("unchecked")
   final public T unset(long flags) {
      value &= ~flags;
      return (T)this;
   }

   @SuppressWarnings("unchecked")
   public Flags_long<T> make() {
      try {
         return getClass().newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
         return null;
      }
   }
   
   public String toString() {
      return this.getClass().getSimpleName() + ":" + Long.toString(value);
   }

   
}
