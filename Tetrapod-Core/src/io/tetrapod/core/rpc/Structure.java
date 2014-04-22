package io.tetrapod.core.rpc;

import io.tetrapod.core.json.JSONObject;
import io.tetrapod.core.serialize.DataSource;
import io.tetrapod.core.serialize.datasources.*;
import io.tetrapod.protocol.core.StructDescription;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import org.slf4j.*;

abstract public class Structure {

   private static final Logger logger = LoggerFactory.getLogger(Structure.class);

   public static enum Security {
      PUBLIC, // open to services and unauthorized users
      PROTECTED, // open to services and authorized users
      INTERNAL, // open to services
      PRIVATE, // open to exact same service only
      ADMIN // open to admin user only
   }

   abstract public void write(DataSource data) throws IOException;

   abstract public void read(DataSource data) throws IOException;

   abstract public int getStructId();

   abstract public int getContractId();

   public Structure make() {
      try {
         return getClass().newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
         return null;
      }
   }

   public StructDescription makeDescription() {
      return null;
   }

   @Override
   public String toString() {
      return getClass().getSimpleName();
   }

   public String dump() {
      return dump(true, true);
   }

   public String dump(boolean expandSubTypes, boolean includeClassname) {
      StringBuilder sb = new StringBuilder();
      Field f[] = getClass().getDeclaredFields();

      for (int i = 0; i < f.length; i++) {
         try {
            int mod = f[i].getModifiers();
            if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
               Object val = dumpValue(f[i].get(this), expandSubTypes);
               sb.append(f[i].getName() + ":" + val + ", ");
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
      String s = sb.length() > 0 ? ("{ " + sb.substring(0, sb.length() - 2) + " } ") : "{}";
      return (includeClassname ? this.getClass().getSimpleName() + " " : "") + s;
   }

   public Security getSecurity() {
      return Security.INTERNAL;
   }

   public String[] tagWebNames() {
      return new String[] {};
   }

   @SuppressWarnings("rawtypes")
   protected Object dumpValue(Object val, boolean expandSubTypes) {
      if (val != null && val instanceof List) {
         val = "[len=" + ((List) val).size() + "]";
      }
      if (val != null && val.getClass().isArray()) {
         val = "[len=" + Array.getLength(val) + "]";
      }
      if (expandSubTypes && val != null && val instanceof Structure) {
         val = ((Structure) val).dump(false, false);
      }
      if (expandSubTypes && val instanceof String) {         
         if (((String) val).length() > 2048) {
            return ((String) val).substring(0, 2048) + "...";
         }
      }
      return val;
   }

   public JSONObject toJSON() {
      try {
         JSONDataSource ds = new JSONDataSource();
         write(ds);
         return ds.getJSON();
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
         return null;
      }
   }

   public byte[] toBytes() {
      try {
         TempBufferDataSource temp = TempBufferDataSource.forWriting();
         write(temp);
         return temp.rawBuffer();
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
         return null;
      }
   }
}
