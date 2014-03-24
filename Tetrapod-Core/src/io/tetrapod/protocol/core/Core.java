package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class Core extends Structure {
   
   /**
    * request is not addressed to a specific entity
    */
   public static final int UNADDRESSED = 0; 
   public static final byte TYPE_TETRAPOD = 1; 
   public static final byte TYPE_SERVICE = 2; 
   public static final byte TYPE_ADMIN = 3; 
   public static final byte TYPE_CLIENT = 4; 
   public static final byte TYPE_ANONYMOUS = 5; 
   public static final int STATUS_INIT = 1; 
   public static final int STATUS_PAUSED = 2; 
   public static final int STATUS_GONE = 4; 
   public static final int STATUS_BUSY = 8; 
   public static final int STATUS_OVERLOADED = 16; 
   public static final int STATUS_FAILED = 32; 
   public static final byte ENVELOPE_HANDSHAKE = 1; 
   public static final byte ENVELOPE_REQUEST = 2; 
   public static final byte ENVELOPE_RESPONSE = 3; 
   public static final byte ENVELOPE_MESSAGE = 4; 
   public static final byte ENVELOPE_PING = 5; 
   public static final byte ENVELOPE_PONG = 6; 
   
   /**
    * Caller does not have sufficient rights to call this Request
    */
   @ERR public static final int ERROR_INVALID_RIGHTS = TetrapodContract.ERROR_INVALID_RIGHTS; 
   
   /**
    * Protocol versions are not compatible
    */
   @ERR public static final int ERROR_PROTOCOL_MISMATCH = TetrapodContract.ERROR_PROTOCOL_MISMATCH; 
   
   /**
    * Unable to deserialize the request
    */
   @ERR public static final int ERROR_SERIALIZATION = TetrapodContract.ERROR_SERIALIZATION; 
   
   /**
    * No service exists to which to relay the request
    */
   @ERR public static final int ERROR_SERVICE_UNAVAILABLE = TetrapodContract.ERROR_SERVICE_UNAVAILABLE; 
   
   /**
    * Request timed out without returning a response
    */
   @ERR public static final int ERROR_TIMEOUT = TetrapodContract.ERROR_TIMEOUT; 
   
   /**
    * catch all error
    */
   @ERR public static final int ERROR_UNKNOWN = TetrapodContract.ERROR_UNKNOWN; 
   
   /**
    * Service exists and received request, but doen't know how to handle it
    */
   @ERR public static final int ERROR_UNKNOWN_REQUEST = TetrapodContract.ERROR_UNKNOWN_REQUEST; 
   
   public static final int STRUCT_ID = 9088168;
    
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
   
   @Override
   public final int getStructId() {
      return Core.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new Core(); }
      };
   }
   
}
