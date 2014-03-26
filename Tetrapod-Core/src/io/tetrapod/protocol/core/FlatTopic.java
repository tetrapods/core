package io.tetrapod.protocol.core;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * a flattened Topic object for serialization
 */

@SuppressWarnings("unused")
public class FlatTopic extends Structure {
   
   public static final int STRUCT_ID = 3803415;
    
   public FlatTopic() {
      defaults();
   }

   public FlatTopic(int topicId, Subscriber[] subscriber) {
      this.topicId = topicId;
      this.subscriber = subscriber;
   }   
   
   public int topicId;
   public Subscriber[] subscriber;

   public final Structure.Security getSecurity() {
      return Security.INTERNAL;
   }

   public final void defaults() {
      topicId = 0;
      subscriber = null;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.topicId);
      if (this.subscriber != null) data.write(2, this.subscriber);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.topicId = data.read_int(tag); break;
            case 2: this.subscriber = data.read_struct_array(tag, new Subscriber()); break;
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
      return FlatTopic.STRUCT_ID;
   }
   
   public final int getContractId() {
      return TetrapodContract.CONTRACT_ID;
   }

   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[2+1];
      result[1] = "topicId";
      result[2] = "subscriber";
      return result;
   }

   public final Structure make() {
      return new FlatTopic();
   }
}
