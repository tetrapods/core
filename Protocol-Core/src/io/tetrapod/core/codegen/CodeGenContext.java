package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.utils.FNVHash;

import java.util.*;
import java.util.regex.*;

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
      Annotations annotations;

      public String classname() {
         return name + (type.equals("struct") ? "" : CodeGen.toTitleCase(type));
      }

      public int compareTo(Class o) {
         return name.compareTo(o.name);
      }
      
      public int maxTag() {
         int m = 0;
         for (Field f : fields) {
            int t = Integer.parseInt(f.tag);
            if (t > m)
               m = t;
         }
         return m;
      }

      public String getStructId() {
         if (structId == null) {
            // auto-genned hashes are never less than 10
            structId = "" + ((FNVHash.hash32(classname()) & 0xffffff) + 10);
         }
         return structId;
      }
   }
   
   public static class ClassLike  {
      String      name;
      String      comment;
      List<Field> fields = new ArrayList<>();
   }
   
   public static class Field {
      String name;
      String type;
      String collectionType;
      String defaultValue;
      String tag;
      String comment;
      String interiorType; // eg flags can be int or long
      Annotations annotations;

      public boolean isConstant() {
         return tag.equals("0");
      }

      public boolean isFlag() {
         return tag.equals("flag");
      }

      public boolean isEnum() {
         return tag.equals("enum");
      }
      
      public String getWebName() {
         if (annotations.getFirst("noweb") != null)
            return null;
         String name = annotations.getFirst("web");
         return (name == null) ? this.name : name;
      }

      public String getEscapedDefaultValue() {
         if (defaultValue == null)
            return null;
         if (type.equals("string"))
            return JavaGenerator.escapeString(defaultValue);
         return defaultValue;
      }

      public String embeddedClassName() {
         return name.substring(0, name.indexOf('.'));
      }
   }
   
   public static class Err implements Comparable<Err> {
      String name;
      String comment;
      int value = 0;
      Annotations annotations;
      
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

      public int getValue() {
         if (value == 0) {
            value = (FNVHash.hash32(name) & 0xffffff) + 100;
            // genned hashes are never less than 100
         }
         return value;
      }
   }

   public ArrayList<Class> classes         = new ArrayList<>();
   public ArrayList<Field> globalConstants = new ArrayList<>();
   public String serviceName;
   public String serviceComment;
   public Annotations            serviceAnnotations = new Annotations();
   public String                 defaultSecurity    = "internal";
   public Set<Err>               allErrors          = new TreeSet<>();
   public Set<String>            subscriptions      = new TreeSet<>();
   public boolean                inGlobalScope      = true;
   public Map<String, ClassLike> enums              = new TreeMap<>();
   public Map<String, ClassLike> flags              = new TreeMap<>();

   private Map<String, Set<Class>> classesByType = new HashMap<>();

   public void parseClass(TokenizedLine line) throws ParseException {
      ArrayList<String> parts = line.parts;
      Class c = new Class();
      c.comment = line.comment;
      c.annotations = line.annotations;
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
      c.structId = c.annotations.getFirst("id");
      verifySecurity(c);
      classes.add(c);
      Set<Class> set = classesByType.get(c.type);
      if (set == null) {
         set = new TreeSet<Class>();
         classesByType.put(c.type, set);
      }
      for (Class other : set)
         if (other.name.equals(c.name))
            throw new ParseException("duplicate class name: " + c.name);
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
         Pattern p = Pattern.compile("(\\d?)\\s*\\^\\s*(\\d+)");
         Matcher m = p.matcher(defaultValue);
         if (m.matches()) {
            String a = m.group(1).isEmpty() ? "2" : m.group(1);
            String b = m.group(2);
            defaultValue = Integer.toString((int) Math.pow(Integer.parseInt(a), Integer.parseInt(b)));
            if (line.comment == null || line.comment.isEmpty())
               line.comment = a + "^" + b;
         }
      }
      Field f = new Field();
      f.comment = line.comment;
      f.annotations = line.annotations;
      f.tag = tag;
      f.type = type;
      f.name = name;
      f.collectionType = collectionType;
      f.defaultValue = defaultValue;
      if (f.isFlag() || f.isEnum()) {
         String className = f.embeddedClassName();
         f.name = f.name.substring(f.name.indexOf('.') + 1);
         ClassLike c = (f.isFlag() ? flags : enums).get(className);
         if (c == null) {
            c = new ClassLike();
            c.name = className;
            (f.isFlag() ? flags : enums).put(className, c);
         }
         c.fields.add(f);
      } else if (inGlobalScope) {
         if (!f.isConstant())
            throw new ParseException("non-constant field declared in global scope");
         globalConstants.add(f);
      } else {
         classes.get(classes.size() - 1).fields.add(f);
      }
      if (f.defaultValue != null && (f.collectionType != null || Character.isUpperCase(f.type.charAt(0))))
         throw new ParseException("non-primitives cannot be assigned default values, field=" + f.name);

   }

   public void parseService(TokenizedLine line) throws ParseException {
      serviceName = line.parts.get(1);
      serviceAnnotations.addAll(line.annotations);
      serviceComment = line.comment;
   }

   public void parseErrors(TokenizedLine line) throws ParseException {
      ArrayList<String> parts = line.parts;

      Class myClass = inGlobalScope ? null : classes.get(classes.size() - 1);
      Err e = null;
      for (int i = 1; i < parts.size(); i++) {
         String err = parts.get(i);
         if (err.equals("=")) {
            e.value = Integer.parseInt(parts.get(i+1));
            break;
         }
         if (err.equals(","))
            continue;
         e = new Err();
         e.name = err;
         e.comment = line.comment;
         if (myClass != null)
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

   public void done() {
      // fill interiorTypes for fields which are flags
      for (Class c : classes) {
         for (Field f : c.fields) {
            ClassLike fg = flags.get(f.type);
            if (fg != null) {
               f.interiorType = "flags." + fg.fields.get(0).type;
               continue;
            }
            fg = enums.get(f.type);
            if (fg != null) {
               f.interiorType = "enum." + fg.fields.get(0).type;
            }
         }
      }
   }

}
