package io.tetrapod.core.protocol;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public class RequestHeader extends Structure {
   
   public static final int STRUCT_ID = 7165109;
    
   public RequestHeader() {
      defaults();
   }
   
   public int requestId;
   public int fromId;
   public int toId;
   public byte fromType;
   public byte timeout;
   public int version;
   public int structId;

   public final void defaults() {
      requestId = 0;
      fromId = 0;
      toId = 0;
      fromType = 0;
      timeout = 0;
      version = 0;
      structId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, requestId);
      data.write(2, fromId);
      data.write(3, toId);
      data.write(4, fromType);
      data.write(5, timeout);
      data.write(6, version);
      data.write(7, structId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: requestId = data.read_int(tag); break;
            case 2: fromId = data.read_int(tag); break;
            case 3: toId = data.read_int(tag); break;
            case 4: fromType = data.read_byte(tag); break;
            case 5: timeout = data.read_byte(tag); break;
            case 6: version = data.read_int(tag); break;
            case 7: structId = data.read_int(tag); break;
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
      return RequestHeader.STRUCT_ID;
   }
}
