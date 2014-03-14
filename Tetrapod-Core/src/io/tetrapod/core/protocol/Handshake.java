package io.tetrapod.core.protocol;

import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public class Handshake extends Structure {
   
   public static final int STRUCT_ID = 7261648;
    
   public Handshake() {
      defaults();
   }
   
   public int wireVersion;
   public int wireOptions;

   public final void defaults() {
      wireVersion = 0;
      wireOptions = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, wireVersion);
      data.write(2, wireOptions);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: wireVersion = data.read_int(tag); break;
            case 2: wireOptions = data.read_int(tag); break;
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
}

