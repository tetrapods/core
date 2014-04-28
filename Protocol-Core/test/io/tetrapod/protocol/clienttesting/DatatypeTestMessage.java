package io.tetrapod.protocol.clienttesting;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.serialize.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.protocol.core.TypeDescriptor;
import io.tetrapod.protocol.core.StructDescription;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class DatatypeTestMessage extends Message {
   
   public static final int STRUCT_ID = 2020;
   public static final int CONTRACT_ID = RemoteTestContract.CONTRACT_ID;
    
   public DatatypeTestMessage() {
      defaults();
   }

   public DatatypeTestMessage(int smallInt, int largeInt, long smallLong, long largeLong, byte smallByte, byte largeByte, boolean boolTrue, boolean boolFalse, String asciiString, String basicUnicode, String unicodeNormalization, String unicodeOutsideBMP, double smallDouble) {
      this.smallInt = smallInt;
      this.largeInt = largeInt;
      this.smallLong = smallLong;
      this.largeLong = largeLong;
      this.smallByte = smallByte;
      this.largeByte = largeByte;
      this.boolTrue = boolTrue;
      this.boolFalse = boolFalse;
      this.asciiString = asciiString;
      this.basicUnicode = basicUnicode;
      this.unicodeNormalization = unicodeNormalization;
      this.unicodeOutsideBMP = unicodeOutsideBMP;
      this.smallDouble = smallDouble;
   }   
   
   public int smallInt;
   public int largeInt;
   public long smallLong;
   public long largeLong;
   public byte smallByte;
   public byte largeByte;
   public boolean boolTrue;
   public boolean boolFalse;
   public String asciiString;
   public String basicUnicode;
   public String unicodeNormalization;
   public String unicodeOutsideBMP;
   public double smallDouble;

   public final Structure.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      smallInt = 42;
      largeInt = 32767;
      smallLong = 42;
      largeLong = 9223372036854775807L;
      smallByte = 3;
      largeByte = 127;
      boolTrue = true;
      boolFalse = false;
      asciiString = "happy\" esc\\apes # //";
      basicUnicode = "t??t????pad ????????????";
      unicodeNormalization = null;
      unicodeOutsideBMP = null;
      smallDouble = 2.1;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, this.smallInt);
      data.write(2, this.largeInt);
      data.write(3, this.smallLong);
      data.write(4, this.largeLong);
      data.write(5, this.smallByte);
      data.write(6, this.largeByte);
      data.write(7, this.boolTrue);
      data.write(8, this.boolFalse);
      data.write(9, this.asciiString);
      data.write(10, this.basicUnicode);
      data.write(11, this.unicodeNormalization);
      data.write(12, this.unicodeOutsideBMP);
      data.write(13, this.smallDouble);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: this.smallInt = data.read_int(tag); break;
            case 2: this.largeInt = data.read_int(tag); break;
            case 3: this.smallLong = data.read_long(tag); break;
            case 4: this.largeLong = data.read_long(tag); break;
            case 5: this.smallByte = data.read_byte(tag); break;
            case 6: this.largeByte = data.read_byte(tag); break;
            case 7: this.boolTrue = data.read_boolean(tag); break;
            case 8: this.boolFalse = data.read_boolean(tag); break;
            case 9: this.asciiString = data.read_string(tag); break;
            case 10: this.basicUnicode = data.read_string(tag); break;
            case 11: this.unicodeNormalization = data.read_string(tag); break;
            case 12: this.unicodeOutsideBMP = data.read_string(tag); break;
            case 13: this.smallDouble = data.read_double(tag); break;
            case Codec.END_TAG:
               return;
            default:
               data.skip(tag);
               break;
         }
      }
   }
   
   public final int getContractId() {
      return DatatypeTestMessage.CONTRACT_ID;
   }

   public final int getStructId() {
      return DatatypeTestMessage.STRUCT_ID;
   }
   
   @Override
   public final void dispatch(SubscriptionAPI api, MessageContext ctx) {
      if (api instanceof Handler)
         ((Handler)api).messageDatatypeTest(this, ctx);
      else
         api.genericMessage(this, ctx);
   }
   
   public static interface Handler extends SubscriptionAPI {
      void messageDatatypeTest(DatatypeTestMessage m, MessageContext ctx);
   }
   
   public final String[] tagWebNames() {
      // Note do not use this tags in long term serializations (to disk or databases) as 
      // implementors are free to rename them however they wish.  A null means the field
      // is not to participate in web serialization (remaining at default)
      String[] result = new String[13+1];
      result[1] = "smallInt";
      result[2] = "largeInt";
      result[3] = "smallLong";
      result[4] = "largeLong";
      result[5] = "smallByte";
      result[6] = "largeByte";
      result[7] = "boolTrue";
      result[8] = "boolFalse";
      result[9] = "asciiString";
      result[10] = "basicUnicode";
      result[11] = "unicodeNormalization";
      result[12] = "unicodeOutsideBMP";
      result[13] = "smallDouble";
      return result;
   }
   
   public final Structure make() {
      return new DatatypeTestMessage();
   }
   
   public final StructDescription makeDescription() {
      StructDescription desc = new StructDescription();
      desc.tagWebNames = tagWebNames();
      desc.types = new TypeDescriptor[desc.tagWebNames.length];
      desc.types[0] = new TypeDescriptor(TypeDescriptor.T_STRUCT, getContractId(), getStructId());
      desc.types[1] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[2] = new TypeDescriptor(TypeDescriptor.T_INT, 0, 0);
      desc.types[3] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[4] = new TypeDescriptor(TypeDescriptor.T_LONG, 0, 0);
      desc.types[5] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[6] = new TypeDescriptor(TypeDescriptor.T_BYTE, 0, 0);
      desc.types[7] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[8] = new TypeDescriptor(TypeDescriptor.T_BOOLEAN, 0, 0);
      desc.types[9] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[10] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[11] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[12] = new TypeDescriptor(TypeDescriptor.T_STRING, 0, 0);
      desc.types[13] = new TypeDescriptor(TypeDescriptor.T_DOUBLE, 0, 0);
      return desc;
   }
}
