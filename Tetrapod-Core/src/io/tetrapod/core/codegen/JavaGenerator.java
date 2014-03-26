package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.Class;
import io.tetrapod.core.codegen.CodeGenContext.Err;
import io.tetrapod.core.codegen.CodeGenContext.Field;
import io.tetrapod.core.templates.*;
import io.tetrapod.core.utils.FNVHash;

import java.io.*;
import java.util.*;

class JavaGenerator implements LanguageGenerator {

   private String packageName;
   private String outputDir;
   
   public void parseOption(TokenizedLine line) throws ParseException {
      if (!line.parts.get(0).equals("java"))
         return;
      String opt = line.parts.get(1);
      String val = line.parts.get(2);
      switch (opt) {
         case "package":
            packageName = val;
            break;
         case "outdir":
            outputDir = val;
            break;
         default:
            throw new ParseException("unknown java option");
      }
   }

   public void generate(CodeGenContext context) throws IOException,ParseException {
      for (File f : getFilename("c").getParentFile().listFiles()) {
         f.delete();
      }
      for (Class c : context.classes) {
         generateClass(c, context.serviceName + "Contract");
      }
      generateContract(context);
   }
   
   private void generateContract(CodeGenContext context) throws IOException,ParseException {
      Template t = template("contract");
      String theClass = context.serviceName + "Contract";
      t.add("class", theClass);
      t.add("package", packageName);
      t.add("version", context.serviceVersion);
      t.add("name", context.serviceName);
      if (context.serviceId.equals("dynamic")) {
         t.add("contractId", "Contract.UNASSIGNED");
         t.add("contractIdVolatile", "volatile");
         t.add("contractIdSet", theClass + ".CONTRACT_ID = id;");
         throw new ParseException("dynamic contract id's not supported yet");
      } else {
         t.add("contractId", context.serviceId);
         t.add("contractIdVolatile", "final");
         t.add("contractIdSet", "");
      }
      for (String sub : context.subscriptions)
         t.add("subscriptions", genSubscriptions(context, sub, theClass));      
      for (Class c : context.classesByType("request")) {
         t.add("handlers", c.classname() + ".Handler", ",\n");
         t.add("requestAdds", template("contract.adds.call").add("class", c.classname()));
         String path = c.annotations.getFirst("web");
         if (path != null) {
            if (path.isEmpty()) 
               path = Character.toLowerCase(c.name.charAt(0)) + c.name.substring(1);
            path = context.serviceAnnotations.getFirst("web") + path;
            Template sub = template("contract.webroutes.call")
                  .add("path", path).add("requestClass", c.classname())
                  .add("contractClass", theClass);
            t.add("webRoutes", sub.expand()); 
         }
      }
      for (Class c : context.classesByType("response")) {
         t.add("responseAdds", template("contract.adds.call").add("class", c.classname()));
      }
      for (Class c : context.classesByType("message")) {
         t.add("messageAdds", template("contract.adds.call").add("class", c.classname()));
      }

      t.add("classcomment", generateComment(context.serviceComment));
      addErrors(context.allErrors, true, context.serviceName, t);
      addConstantValues(context.globalConstants, t);
      t.expandAndTrim(getFilename(theClass));
   }

   private String genSubscriptions(CodeGenContext context, String subscription, String enclosingClass) throws IOException {
      Template t = template("contract.subscription");
      t.add("name", subscription);
      t.add("enclosingClass", enclosingClass);
      for (Class c : context.classesByType("message")) {
         if (subscription.equals(c.subscription)) {
            t.add("handlers", c.classname() + ".Handler", ",\n");
            t.add("adds", template("contract.adds.call").add("class", c.classname()));
         }
      }
      return t.expand();
   }

   private void generateClass(Class c, String serviceName) throws IOException,ParseException {
      Template t = template(c.type.toLowerCase());
      t.add("rawname", c.name);
      t.add("class", c.classname());
      t.add("package", packageName);
      t.add("security", c.security.toUpperCase());
      t.add("classcomment", generateComment(c.comment));
      t.add("maxtag", "" + c.maxTag());
      if (c.structId == null) {
         // auto-genned hashes are never less than 10
         c.structId = "" + ((FNVHash.hash32(c.classname()) & 0xffffff) + 10);
      }
      t.add("structid", c.structId);
      t.add("service", serviceName);
      addFieldValues(c.fields, t);
      addConstantValues(c.fields, t);
      addErrors(c.errors, false, serviceName, t);
      int instanceFields = 0;
      for (Field f : c.fields) {
         if (!f.isConstant()) instanceFields++;
         String name = f.annotations.getFirst("web");
         if (name == null)
            name = f.name;
         if (f.annotations.getFirst("noweb") != null)
            name = "null";
         if (!f.isConstant())
            t.add("webNames", template("struct.webnames").add("tag", f.tag).add("name", name));
      }
      if (instanceFields > 0) {
         t.add("full-constructor", template("full.constructor").add(t));
      }
      t.expandAndTrim(getFilename(c.classname()));
   }

   private void addErrors(Collection<Err> errors, boolean globalScope, String serviceName, Template global) throws IOException,ParseException {
      for (Err err : errors) {
         Template t = template("field.errors");
         t.add("name", err.name);
         // error hashes are never less than 100
         int hash = err.value;
         if (hash == 0)
            hash = (FNVHash.hash32(err.name) & 0xffffff) + 100;
         t.add("hash", "" + hash);
         t.add("service", serviceName);
         String[] lines = t.expand().split("\r\n|\n|\r");
         String line = globalScope ? lines[0] : lines[1];
         global.add("errors", generateComment(err.comment) + line);
      }
   }

   private void addFieldValues(List<Field> fields, Template global) throws ParseException, IOException {
      for (Field f : fields) {
         if (f.isConstant())
            continue;
         Template sub = getFieldTemplate(f);
         String[] lines = sub.expand().split("\r\n|\n|\r");
         String comment = "";
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            comment = generateComment(f.comment.trim());
         }
         global.add("field-declarations", comment + lines[0]);
         global.add("field-defaults", lines[1]);
         global.add("field-reads", lines[2]);
         global.add("field-writes", lines[3]);
         global.add("inline-declarations", lines[4], ", ");
         global.add("inline-initializers", lines[5]);
      }
   }

   private void addConstantValues(List<Field> fields, Template global) throws ParseException, IOException {
      for (Field f : fields) {
         if (!f.isConstant())
            continue;
         Template sub = getFieldTemplate(f);
         String[] lines = sub.expand().split("\r\n|\n|\r");
         String comment = "";
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            comment = generateComment(f.comment.trim());
         }
         global.add("constants", comment + lines[0]);
      }
   }

   private Template getFieldTemplate(Field f) throws IOException {
      JavaTypes.Info info = JavaTypes.get(f.type);
      String defaultVal = info.defaultValue;
      if (f.defaultValue != null) {
         if (f.type.equals("string"))
            f.defaultValue = escape(f.defaultValue);
         defaultVal = info.defaultValueDelim + f.defaultValue + info.defaultValueDelim;
      }
      String primTemplate = "field.primitives";
      String structTemplate = "field.structs";
      if (f.collectionType != null) {
         defaultVal = "null";
         boolean isEmpty = f.defaultValue != null && f.defaultValue.equals("<empty>");
         switch (f.collectionType) {
            case "<array>":
               primTemplate = "field.array.primitives";
               structTemplate = "field.array.structs";
               if (isEmpty)
                  defaultVal = "new " + info.base + "[0]";
               break;
            case "<list>":
               primTemplate = "field.list.primitives";
               structTemplate = "field.list.structs";
               if (isEmpty)
                  defaultVal = "new ArrayList<>()";
               break;
         }
      } else {
         boolean isEmpty = f.defaultValue != null && f.defaultValue.equals("<empty>");
         if (isEmpty) {
            defaultVal = "new " + info.base + "()";
         }
      }
      if (f.isConstant()) {
         primTemplate = structTemplate = "field.constants";
      }
      Template t = template(info.isPrimitive ? primTemplate : structTemplate);
      t.add("tag", f.tag);
      t.add("default", defaultVal);
      t.add("name", f.name);
      t.add("javatype", info.base);
      t.add("type", f.type);
      t.add("boxed", info.boxed);
      return t;
   }

   private File getFilename(String classname) {
      File f = new File(outputDir);
      for (String p : packageName.split("\\.")) {
         f = new File(f, p);
      }
      f.mkdirs();
      return new File(f, classname + ".java");
   }
   
   private String generateComment(String comment) {
      if (comment == null)
         return "";
      comment = comment.trim();
      if (comment.isEmpty())
         return "";
      StringBuilder sb = new StringBuilder();
      sb.append("\n/**\n * " + comment + "\n */\n");
      return sb.toString();
   }
   
   private String escape(String s) {
      s = s.replace("\\", "\\\\");
      s = s.replace("\"", "\\\"");
      return s;
   }
   
   private Template template(String name) throws IOException {
      return Template.get(getClass(), "javatemplates/" + name + ".template");
   }
}
