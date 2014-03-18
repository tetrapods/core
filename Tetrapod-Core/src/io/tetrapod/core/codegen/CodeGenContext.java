package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;

import java.util.*;

class CodeGenContext {

   public static class Class {
      String       name;
      String       type;
      String       subscription;
      String       structId;
      String       security;
      String       comment;
      List<Field>  fields = new ArrayList<>();
      Set<String>  errors = new TreeSet<>();

      public String classname() {
         return name + (type.equals("struct") ? "" : CodeGen.toTitleCase(type));
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

   public ArrayList<Class> classes         = new ArrayList<>();
   public ArrayList<Field> globalConstants = new ArrayList<>();
   public String           serviceName;
   public String           serviceVersion;
   public String           serviceComment;
   public String           defaultSecurity = "internal";
   public Set<String>      allErrors       = new TreeSet<>();
   public Set<String>      subscriptions   = new TreeSet<>();
   public boolean          inGlobalScope   = true;

   public void parseClass(TokenizedLine line) throws ParseException {
      ArrayList<String> parts = line.parts;
      Class c = new Class();
      c.security = defaultSecurity;
      c.comment = line.comment;
      switch (parts.get(0)) {
         case "public":
         case "protected":
         case "internal":
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
               c.name = n;
            }
            inGlobalScope = false;
            break;
            
         default:
            throw new ParseException("unknown class: " + c.type);
      }
      int n = parts.size();
      if (n > 3 && parts.get(n - 3).equals("[") && parts.get(n - 1).equals("]")) {
         c.structId = parts.get(n - 2);
      }
      classes.add(c);
   }

   public void parseField(TokenizedLine line) throws ParseException {
      // field 1 string name = scott
      // field 2 string passwordHash = unknown
      // field 6 string <maps> int map
      // field 5 int <list> password
      // field 3 int priority
      // const int X = 7
      // const int <list> = {}
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
      // service NAME version NUM
      if (line.parts.size() != 4)
         throw new ParseException("expected exactly four tokens for service");
      serviceName = line.parts.get(1);
      serviceVersion = line.parts.get(3);
      serviceComment = line.comment;
   }

   public void parseErrors(TokenizedLine line) throws ParseException {
      // error A B C ...
      ArrayList<String> parts = line.parts;
      if (inGlobalScope || !classes.get(classes.size() - 1).type.equals("request"))
         throw new ParseException("errors must be defined inside a request");
      
      Class myClass = classes.get(classes.size() - 1);
      for (int i = 1; i < parts.size(); i++) {
         String err = parts.get(i);
         myClass.errors.add(err);
         allErrors.add(err);
      }
   }

}
