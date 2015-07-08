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
   public T set(int flags) {
      value |= flags;
      return (T)this;
   }
   
   @SuppressWarnings("unchecked")
   final public T unset(int flags) {
      value &= ~flags;
      return (T)this;
   }

}
