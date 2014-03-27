package io.tetrapod.core.rpc;

import io.tetrapod.core.serialize.DataSource;
import io.tetrapod.protocol.core.StructDescription;

import java.io.IOException;
import java.lang.reflect.Field;

abstract public class Structure {
   
   public static enum Security {
      PUBLIC,     // open to services and unauthorized users
      PROTECTED,  // open to services and authorized users
      INTERNAL,   // open to services
      PRIVATE,    // open to exact same service only
      ADMIN       // open to admin user only
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
      StringBuilder sb = new StringBuilder();
      dump(this, this.getClass(), sb);
      return this.getClass().getSimpleName() + "\n" + sb.toString();
   }

   private void dump(Object o, Class<?> clazz, StringBuilder sb) {
      Field f[] = clazz.getDeclaredFields();

      for (int i = 0; i < f.length; i++) {
         try {
            sb.append("  ");
            sb.append(f[i].getName() + "=" + f[i].get(o) + "\n");
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }
   
   public Security getSecurity() {
      return Security.INTERNAL;
   }
   
   public String[] tagWebNames() {
      return new String[] {};
   }


}
