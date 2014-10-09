package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Core extends Structure {
   
   /**
    * request is not addressed to a specific entity
    */
   public static final int UNADDRESSED = 0; 
   
   /**
    * request is for direct dispatch
    */
   public static final int DIRECT = 1; 
   public static final byte TYPE_TETRAPOD = 1; 
   public static final byte TYPE_SERVICE = 2; 
   public static final byte TYPE_ADMIN = 3; 
   public static final byte TYPE_CLIENT = 4; 
   public static final byte TYPE_ANONYMOUS = 5; 
   public static final byte TYPE_WEBAPI = 6; 
   public static final int DEFAULT_PUBLIC_PORT = 9900; 
   public static final int DEFAULT_SERVICE_PORT = 9901; 
   public static final int DEFAULT_CLUSTER_PORT = 9902; 
   public static final int DEFAULT_HTTP_PORT = 9904; 
   public static final int DEFAULT_HTTPS_PORT = 9906; 
   public static final int DEFAULT_DIRECT_PORT = 9800; 
   public static final int STATUS_STARTING = 1; 
   public static final int STATUS_PAUSED = 2; 
   public static final int STATUS_GONE = 4; 
   public static final int STATUS_BUSY = 8; 
   public static final int STATUS_OVERLOADED = 16; 
   public static final int STATUS_FAILED = 32; 
   public static final int STATUS_STOPPING = 64; 
   public static final int STATUS_PASSIVE = 128; 
   public static final byte ENVELOPE_HANDSHAKE = 1; 
   public static final byte ENVELOPE_REQUEST = 2; 
   public static final byte ENVELOPE_RESPONSE = 3; 
   public static final byte ENVELOPE_MESSAGE = 4; 
   public static final byte ENVELOPE_BROADCAST = 5; 
   public static final byte ENVELOPE_PING = 6; 
   public static final byte ENVELOPE_PONG = 7; 
   
   public static final int STRUCT_ID = 9088168;
   public static final int CONTRACT_ID = CoreContract.CONTRACT_ID;
    
   public Core() {
      defaults();
   }

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return Core.CONTRACT_ID;
   }

   public final int getStructId() {
      return Core.STRUCT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[0+1];
      
      return result;
   }

   public final Structure make() {
      return new Core();
   }

   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      
      return desc;
   }
}
