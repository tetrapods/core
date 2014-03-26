package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Handshake extends Structure {
   
   public static final int STRUCT_ID = 7261648;
    
   public Handshake() {
      defaults();
   }

   public Handshake(int wireVersion, int wireOptions) {
      this.wireVersion = wireVersion;
      this.wireOptions = wireOptions;
   }   
   
   public int wireVersion;
   public int wireOptions;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      wireVersion = 0;
      wireOptions = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.wireVersion);
      data.write(2, this.wireOptions);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.wireVersion = data.read_int(tag); break;
            case 2: this.wireOptions = data.read_int(tag); break;
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
      return Handshake.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "wireVersion";
      result[2] = "wireOptions";
      return result;
   }

   public final Structure make() {
      return new Handshake();
   }
}
