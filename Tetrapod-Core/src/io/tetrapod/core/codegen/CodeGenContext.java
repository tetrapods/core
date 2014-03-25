package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;

import java.util.*;

class CodeGenContext {

   public static class Class implements Comparable<Class> {
      String      name;
      String      type;
      String      subscription;
      String      structId;
      String      security;
      String      comment;
      List<Field> fields = new ArrayList<>();
      Set<Err>    errors = new TreeSet<>();

      public String classname() {
         return name + (type.equals("struct") ? "" : CodeGen.toTitleCase(type));
      }

      public int compareTo(Class o) {
         return name.compareTo(o.name);
      }
   }

   public static class Field {
      String name;
      String type;
      String collectionType;
      String defaultValue;
      String tag;
      String comment;

      public boolean isConstant() {
         return tag.equals("0");
      }
   }
   
   public static class Err implements Comparable<Err> {
      String name;
      String comment;
      int value = 0;
      
      @Override
      public int hashCode() {
         return name.hashCode();
      }
      
      @Override
      public boolean equals(Object obj) {
         return name.equals(obj);
      }
      
      @Override
      public int compareTo(Err o) {
         return name.compareTo(o.name);
      }
   }

   public ArrayList<Class>         classes         = new ArrayList<>();
   public ArrayList<Field>         globalConstants = new ArrayList<>();
   public String                   serviceName;
   public String                   serviceVersion;
   public String                   serviceComment;
   public String                   serviceId;
   public String                   defaultSecurity = "internal";
   public Set<Err>                 allErrors       = new TreeSet<>();
   public Set<String>              subscriptions   = new TreeSet<>();
   public boolean                  inGlobalScope   = true;

   private Map<String, Set<Class>> classesByType   = new HashMap<>();

   public void parseClass(TokenizedLine line) throws ParseException {
      ArrayList<String> parts = line.parts;
      Class c = new Class();
      c.comment = line.comment;
      switch (parts.get(0)) {
         case "public":
         case "protected":
         case "internal":
         case "private":
         case "admin":
            c.security = parts.get(0);
            parts.remove(0);
            break;
      }
      c.type = parts.get(0);
      switch (c.type) {
         case "request":
         case "response":
         case "struct":
            c.name = parts.get(1);
            inGlobalScope = false;
            break;
         case "message":
            String n = parts.get(1);
            int ix = n.indexOf('.');
            if (ix >= 0) {
               c.subscription = n.substring(0, ix);
               subscriptions.add(c.subscription);
               c.name = n.substring(ix + 1);
            } else {
               c.subscription = "";
               c.name = n;
            }
            inGlobalScope = false;
            break;

         default:
            throw new ParseException("unknown class: " + c.type);
      }
      if (line.tags.containsKey("id")) {
         c.structId = line.tags.get("id").get(0);
      }
      verifySecurity(c);
      classes.add(c);
      Set<Class> set = classesByType.get(c.type);
      if (set == null) {
         set = new TreeSet<Class>();
         classesByType.put(c.type, set);
      }
      set.add(c);
   }

   public void parseField(TokenizedLine line) throws ParseException {
      List<String> parts = line.parts;
      String tag = parts.get(1);
      String type = parts.get(2);
      int nameIx = 3;
      String name = parts.get(3);
      String collectionType = null;
      String defaultValue = null;

      switch (name) {
         case "<array>":
         case "<list>":
            collectionType = name;
            name = parts.get(4);
            nameIx = 4;
            break;
      }
      if (parts.size() > nameIx + 2 && parts.get(nameIx + 1).equals("=")) {
         defaultValue = parts.get(nameIx + 2);
      }
      Field f = new Field();
      f.comment = line.comment;
      f.tag = tag;
      f.type = type;
      f.name = name;
      f.collectionType = collectionType;
      f.defaultValue = defaultValue;
      if (inGlobalScope) {
         if (!f.isConstant())
            throw new ParseException("non-constant field declared in global scope");
         globalConstants.add(f);
      } else {
         classes.get(classes.size() - 1).fields.add(f);
      }
   }

   public void parseService(TokenizedLine line) throws ParseException {
      if (line.parts.size() < 4)
         throw new ParseException("expected at least four tokens for service");
      serviceName = line.parts.get(1);
      serviceVersion = line.parts.get(3);
      if (line.parts.size() > 4) {
         serviceId = line.parts.get(5);
      } else {
         serviceId = "dynamic";
      }
      serviceComment = line.comment;
   }

   public void parseErrors(TokenizedLine line) throws ParseException {
      ArrayList<String> parts = line.parts;
      if (inGlobalScope)
         throw new ParseException("errors must be defined inside a class");

      Class myClass = classes.get(classes.size() - 1);
      Err e = null;
      for (int i = 1; i < parts.size(); i++) {
         String err = parts.get(i);
         if (err.equals("=")) {
            e.value = Integer.parseInt(parts.get(i+1));
            break;
         }
         e = new Err();
         e.name = err;
         e.comment = line.comment;
         myClass.errors.add(e);
         allErrors.add(e);
      }
   }

   public Collection<Class> classesByType(String type) {
      Set<Class> c = classesByType.get(type);
      return c == null ? new ArrayList<Class>() : c;
   }
   
   private void verifySecurity(Class c) throws ParseException {
      if (c.type.equals("response")) {
         for (Class other : classes) {
            if (other.type.equals("request") && other.name.equals(c.name)) {
               if (c.security == null)
                  c.security = other.security;
               if (!c.security.equals(other.security)) {
                  throw new ParseException("security for request and response [" + c.name + "]  do not match");
               }
               return;
            }
         }
      }
      if (c.security == null) {
         c.security = defaultSecurity;
      }
   }

}
