package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class WebRoute extends Structure {
   
   public static final int STRUCT_ID = 4890284;
    
   public WebRoute() {
      defaults();
   }

   public WebRoute(String path, int structId, int contractId) {
      this.path = path;
      this.structId = structId;
      this.contractId = contractId;
   }   
   
   public String path;
   public int structId;
   public int contractId;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      path = null;
      structId = 0;
      contractId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.path);
      data.write(2, this.structId);
      data.write(3, this.contractId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.path = data.read_string(tag); break;
            case 2: this.structId = data.read_int(tag); break;
            case 3: this.contractId = data.read_int(tag); break;
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
      return WebRoute.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new WebRoute(); }
      };
   }
   
}
