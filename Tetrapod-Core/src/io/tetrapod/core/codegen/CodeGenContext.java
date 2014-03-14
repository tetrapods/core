package io.tetrapod.core.codegen;

import java.util.*;

class CodeGenContext {

   public static class Group {
      String      name;
      String      type;
      List<Class> classes = new ArrayList<>();
   }

   public static class Class {
      String      name;
      String      type;
      List<Field> fields = new ArrayList<>();

      public String classname() {
         return name + CodeGen.toTitleCase(type);
      }
   }

   public static class Field {
      String name;
      String type;
      String mapValueType;
      String collectionType;
      String defaultValue;
      String tag;
   }

   public ArrayList<Group> groups = new ArrayList<>();

   public void startGroup(List<String> parts) throws ParseException {
      String type = parts.get(0);
      String name = null;

      switch (type) {
         case "rpc":
         case "subscription":
            name = parts.get(1);
            // fall through
         case "misc":
            Group g = new Group();
            g.name = name;
            g.type = type;
            groups.add(g);
            break;

         default:
            throw new ParseException("unknown group: " + type);
      }
   }

   public void endGroup() {
   }

   public void startClass(List<String> parts) throws ParseException {
      if (groups.size() == 0)
         throw new ParseException("must add classes to groups");
      String type = parts.get(0);
      String name = null;
      switch (type) {
         case "request":
         case "response":
         case "message":
         case "struct":
            name = parts.get(1);
            // fall through
         case "errors":
            Class c = new Class();
            c.name = name;
            c.type = type;
            groups.get(groups.size() - 1).classes.add(c);
            break;

         default:
            throw new ParseException("unknown class: " + type);
      }
   }

   public void endClass() {
   }

   public void appendField(List<String> parts) throws ParseException {
      if (groups.size() == 0)
         throw new ParseException("must add fields to groups");
      if (groups.get(groups.size() - 1).classes.size() == 0)
         throw new ParseException("must add fields to classes");

      // field 1 string name = scott
      // field 2 string passwordHash = unknown
      // field 6 string <maps> int map
      // field 5 int <list> password
      // field 3 int priority

      String tag = parts.get(1);
      String type = parts.get(2);
      int nameIx = 3;
      String name = parts.get(3);
      String mapValueType = null;
      String collectionType = null;
      String defaultValue = null;

      switch (name) {
         case "<maps>":
            collectionType = name;
            mapValueType = parts.get(4);
            name = parts.get(5);
            nameIx = 5;
            break;
         case "<array>":
         case "<list>":
         case "<set>":
            collectionType = name;
            name = parts.get(4);
            nameIx = 4;
            break;
      }
      // ERR
      if (parts.size() > nameIx + 2 && parts.get(nameIx + 1).equals("=")) {
         defaultValue = parts.get(nameIx + 2);
      }
      Field f = new Field();
      f.tag = tag;
      f.type = type;
      f.name = name;
      f.collectionType = collectionType;
      f.mapValueType = mapValueType;
      f.defaultValue = defaultValue;
      List<Class> list = groups.get(groups.size() - 1).classes;
      list.get(list.size() - 1).fields.add(f);
   }

}
