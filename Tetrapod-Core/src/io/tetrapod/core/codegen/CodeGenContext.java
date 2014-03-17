package io.tetrapod.core.codegen;

import java.util.*;

class CodeGenContext {

   public static class Class {
      String       name;
      String       type;
      String       subscription;
      String       structId;
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

      public boolean isConstant() {
         return tag.equals("0");
      }
   }

   public ArrayList<Class> classes         = new ArrayList<>();
   public ArrayList<Field> globalConstants = new ArrayList<>();
   public String           serviceName;
   public String           serviceVersion;
   public Set<String>      allErrors       = new TreeSet<>();
   public boolean          inGlobalScope   = true;

   public void parseClass(List<String> parts) throws ParseException {
      Class c = new Class();
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

   public void parseField(List<String> parts) throws ParseException {
      // field 1 string name = scott
      // field 2 string passwordHash = unknown
      // field 6 string <maps> int map
      // field 5 int <list> password
      // field 3 int priority
      // const int X = 7
      // const int <list> = {}
      
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

   public void parseService(ArrayList<String> parts) throws ParseException {
      // service NAME version NUM
      if (parts.size() != 4)
         throw new ParseException("expected exactly four tokens for service");
      serviceName = parts.get(1);
      serviceVersion = parts.get(3);
   }

   public void parseErrors(ArrayList<String> parts) throws ParseException {
      // error A B C ...
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
