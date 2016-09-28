package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import java.util.*;

@SuppressWarnings("all")
public enum SessionType implements Enum_int<SessionType> {
   UNKNOWN((int)0), 
   WIRE((int)1), 
   WEB((int)2), 
   ;
   
   public static SessionType from(int val) {
      for (SessionType e : SessionType.values())
         if (e.value == (val))
            return e; 
      return null;
   }
   
   public final int value;
   
   /** 
    * Returns the value of the enum, as opposed to the name like the superclass.  To get
    * the name you can use this.name().
    * @return the value as a string
    */
   public String toString() { 
      return "" + value; 
   }
   
   private SessionType(int value) {
      this.value = value;
   }

   @Override
   public int getValue() {
      return value;
   }
}
