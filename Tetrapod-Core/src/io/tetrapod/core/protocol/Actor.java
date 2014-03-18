package io.tetrapod.core.protocol;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Actor extends Structure {
   
   public static final int STRUCT_ID = 4840548;
    
   public Actor() {
      defaults();
   }
   
   public int actorId;
   public int parentId;
   public long reclaimToken;
   public String host;
   public int status;
   public byte type;
   public String name;
   public int build;
   public int version;

   public final void defaults() {
      actorId = 0;
      parentId = 0;
      reclaimToken = 0;
      host = null;
      status = 0;
      type = 0;
      name = null;
      build = 0;
      version = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, actorId);
      data.write(2, parentId);
      data.write(3, reclaimToken);
      data.write(4, host);
      data.write(5, status);
      data.write(6, type);
      data.write(7, name);
      data.write(8, build);
      data.write(9, version);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: actorId = data.read_int(tag); break;
            case 2: parentId = data.read_int(tag); break;
            case 3: reclaimToken = data.read_long(tag); break;
            case 4: host = data.read_string(tag); break;
            case 5: status = data.read_int(tag); break;
            case 6: type = data.read_byte(tag); break;
            case 7: name = data.read_string(tag); break;
            case 8: build = data.read_int(tag); break;
            case 9: version = data.read_int(tag); break;
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
      return Actor.STRUCT_ID;
   }
   
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new Actor(); }
      };
   }
}
