package io.tetrapod.core.rpc;

public class Flags_int<T> {

   public int value;
   
   final public boolean hasAny(int flags) {
      return (value & flags) != 0;
   }

   final public boolean isSet(int flags) {
      return (value & flags) == flags;
   }

   final public boolean isNoneSet(int flags) {
      return (value & flags) == 0;
   }
   
   @SuppressWarnings("unchecked")
   public T set(int flags) {
      value |= flags;
      return (T)this;
   }
   
   @SuppressWarnings("unchecked")
   final public T unset(int flags) {
      value &= ~flags;
      return (T)this;
   }

   @SuppressWarnings("unchecked")
   public Flags_int<T> make() {
      try {
         return getClass().newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
         return null;
      }
   }
   
}
