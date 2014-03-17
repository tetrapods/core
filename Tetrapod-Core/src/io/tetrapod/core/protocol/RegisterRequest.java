package io.tetrapod.core.protocol;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import io.tetrapod.core.rpc.*;
import io.tetrapod.core.serialize.*;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public class RegisterRequest extends Request {

   public static final int STRUCT_ID = 10895179;
   
   public RegisterRequest() {
      defaults();
   }
   
   public int build;

   public final Request.Security getSecurity() {
      return Security.PUBLIC;
   }

   public final void defaults() {
      build = 0;
   }
   
   @Override
   public final void write(DataSource data) throws IOException {
      data.write(1, build);
      data.writeEndTag();
   }
   
   @Override
   public final void read(DataSource data) throws IOException {
      defaults();
      while (true) {
         int tag = data.readTag();
         switch (tag) {
            case 1: build = data.read_int(tag); break;
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
      return RegisterRequest.STRUCT_ID;
   }
   
   @Override
   public final Response dispatch(ServiceAPI is) {
      if (is instanceof Handler)
         return ((Handler)is).requestRegister(this);
      return is.genericRequest(this);
   }
   
   public static interface Handler extends ServiceAPI {
      Response requestRegister(RegisterRequest r);
   }
}
