package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class MessageHeader extends Structure {
   
   public static final int STRUCT_ID = 11760427;
    
   public MessageHeader() {
      defaults();
   }

   public MessageHeader(int fromId, int topicId, int toId, int structId, int contractId) {
      this.fromId = fromId;
      this.topicId = topicId;
      this.toId = toId;
      this.structId = structId;
      this.contractId = contractId;
   }   
   
   public int fromId;
   public int topicId;
   public int toId;
   public int structId;
   public int contractId;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      fromId = 0;
      topicId = 0;
      toId = 0;
      structId = 0;
      contractId = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.fromId);
      data.write(2, this.topicId);
      data.write(3, this.toId);
      data.write(4, this.structId);
      data.write(5, this.contractId);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.fromId = data.read_int(tag); break;
            case 2: this.topicId = data.read_int(tag); break;
            case 3: this.toId = data.read_int(tag); break;
            case 4: this.structId = data.read_int(tag); break;
            case 5: this.contractId = data.read_int(tag); break;
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
      return MessageHeader.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }
   public static Callable<Structure> getInstanceFactory() {
      return new Callable<Structure>() {
         public Structure call() { return new MessageHeader(); }
      };
   }
   
}
