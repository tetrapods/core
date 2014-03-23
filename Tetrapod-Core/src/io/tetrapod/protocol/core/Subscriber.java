package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Subscriber extends Structure {
   
   public static final int STRUCT_ID = 16013581;
    
   public Subscriber() {
      defaults();
   }

   public Subscriber(int entityId, int counter) {
      this.entityId = entityId;
      this.counter = counter;
   }   
   
   public int entityId;
   public int counter;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      entityId = 0;
      counter = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.entityId);
      data.write(2, this.counter);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.entityId = data.read_int(tag); break;
            case 2: this.counter = data.read_int(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   @Override
   public final int getStructId() {
      return Subscriber.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new Subscriber(); }
      };
   }
   
}
