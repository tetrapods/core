package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import java.util.*;

@SuppressWarnings("all")
public enum LogHeaderType implements Enum_int<LogHeaderType> {
   REQUEST((int)1), 
   RESPONSE((int)2), 
   MESSAGE((int)3), 
   EVENT((int)4), 
   ;
   
   public static LogHeaderType from(int val) {
      for (LogHeaderType e : LogHeaderType.values())
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
   
   private LogHeaderType(int value) {
      this.value = value;
   }

   @Override
   public int getValue() {
      return value;
   }
}
