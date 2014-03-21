package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.Class;
import io.tetrapod.core.codegen.CodeGenContext.Field;
import io.tetrapod.core.templates.*;
import io.tetrapod.core.utils.FNVHash;

import java.io.*;
import java.util.*;

class JavaGenerator implements LanguageGenerator {

   private String packageName;
   private String outputDir;
   
   public void parseOption(TokenizedLine line) throws ParseException {
//      java package io.tetrapod.identity.protocol
//      java outdir src
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
      for (Class c : context.classes) {
         generateClass(c, context.serviceName + "Contract");
      }
      generateContract(context);
   }
   
   private void generateContract(CodeGenContext context) throws IOException,ParseException {
      Templater t = template("contract");
      TemplateValues vals = new TemplateValues();
      vals.add("class", context.serviceName + "Contract");
      vals.add("package", packageName);
      vals.add("version", context.serviceVersion);
      vals.add("name", context.serviceName);
      if (context.serviceId.equals("dynamic")) {
         vals.add("contractId", "Contract.UNASSIGNED");
         vals.add("contractIdVolatile", "volatile");
         vals.add("contractIdSet", vals.get("class") + ".CONTRACT_ID = id;");
         throw new ParseException("dynamic contract id's not supported yet");
      } else {
         vals.add("contractId", context.serviceId);
         vals.add("contractIdVolatile", "final");
         vals.add("contractIdSet", "");
      }
      for (String sub : context.subscriptions)
         vals.add("subscriptions", genSubscriptions(context, sub, vals.get("class")));      
      vals.setSeperator("handlers", ",\n");
      vals.setSeperator("requestAdds", "\n");
      vals.setSeperator("responseAdds", "\n");
      vals.setSeperator("messageAdds", "\n");
      for (Class c : context.classesByType("request")) {
         vals.add("handlers", c.classname() + ".Handler");
         vals.add("requestAdds", template("contract.adds.call").expand(new TemplateValues("class", c.classname())));
      }
      for (Class c : context.classesByType("response")) {
         vals.add("responseAdds", template("contract.adds.call").expand(new TemplateValues("class", c.classname())));
      }
      for (Class c : context.classesByType("message")) {
         if (c.subscription.isEmpty())
            vals.add("messageAdds", template("contract.adds.call").expand(new TemplateValues("class", c.classname())));
      }
      vals.setIfEmpty("handlers", "");
      vals.setIfEmpty("requestAdds", "");
      vals.setIfEmpty("responseAdds", "");
      vals.setIfEmpty("messageAdds", "");
      vals.setIfEmpty("subscriptions", "");
      vals.add("classcomment", generateComment(context.serviceComment));
      addErrors(context.allErrors, true, context.serviceName, vals);
      addConstantValues(context.globalConstants, vals);
      t.expandAndTrim(vals, getFilename(vals.get("class")));
   }

   private String genSubscriptions(CodeGenContext context, String subscription, String enclosingClass) throws IOException {
      Templater t = template("contract.subscription");
      TemplateValues vals = new TemplateValues();
      vals.setSeperator("handlers", ",\n");
      vals.setSeperator("adds", "\n");
      vals.add("name", subscription);
      vals.add("enclosingClass", enclosingClass);
      for (Class c : context.classesByType("message")) {
         if (subscription.equals(c.subscription)) {
            vals.add("handlers", c.classname() + ".Handler");
            vals.add("adds", template("contract.adds.call").expand(new TemplateValues("class", c.classname())));
         }
      }
      return t.expand(vals);
   }

   private void generateClass(Class c, String serviceName) throws IOException,ParseException {
      Templater t = Templater.get(getClass(), "javatemplates/" + c.type.toLowerCase() + ".template");
      TemplateValues vals = new TemplateValues();
      vals.add("rawname", c.name);
      vals.add("class", c.classname());
      vals.add("package", packageName);
      vals.add("security", c.security.toUpperCase());
      vals.add("classcomment", generateComment(c.comment));
      if (c.structId == null) {
         // auto-genned hashes are never less than 10
         c.structId = "" + ((FNVHash.hash32(c.classname()) & 0xffffff) + 10);
      }
      vals.add("structid", c.structId);
      vals.add("service", serviceName);
      addFieldValues(c.fields, vals);
      addConstantValues(c.fields, vals);
      addErrors(c.errors, false, serviceName, vals);
      if (c.fields.size() > 0)
         vals.add("full-constructor", Templater.get(getClass(), "javatemplates/full.constructor.template").expand(vals));
      else
         vals.add("full-constructor", "");
      t.expandAndTrim(vals, getFilename(c.classname()));
   }

   private void addErrors(Collection<String> errors, boolean globalScope, String serviceName, TemplateValues vals) throws IOException,ParseException {
      Templater t = template("field.errors");
      vals.setSeperator("errors", "\n");
      for (String err : errors) {
         TemplateValues v = new TemplateValues();
         v.add("name", err);
         // error hashes are never less than 100
         v.add("hash", ""+ ((FNVHash.hash32(err) & 0xffffff) + 100));
         v.add("service", serviceName);
         String[] lines = t.expand(v).split("\r\n|\n|\r");
         String line = globalScope ? lines[0] : lines[1];
         vals.add("errors", line);
      }
      vals.setIfEmpty("errors", "");
   }

   private void addFieldValues(List<Field> fields, TemplateValues globalVals) throws ParseException, IOException {
      StringBuilder declarations = new StringBuilder();
      StringBuilder defaults = new StringBuilder();
      StringBuilder writes = new StringBuilder();
      StringBuilder reads = new StringBuilder();
      StringBuilder inlineDeclarations = new StringBuilder();
      StringBuilder inlineInitializers = new StringBuilder();
      boolean isFirst = true;
      for (Field f : fields) {
         if (f.isConstant())
            continue;
         TemplateValues vals = getTemplateValues(f);
         String[] lines = template(vals.get("template")).expand(vals).split("\r\n|\n|\r");
         String newline = "\n";
         String comma = ", ";
         if (isFirst) {
            isFirst = false;
            newline = "";
            comma = "";
         }
         declarations.append(newline);
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            declarations.append(generateComment(f.comment.trim()));
         }
         declarations.append(lines[0]);
         defaults.append(newline);
         defaults.append(lines[1]);
         reads.append(newline);
         reads.append(lines[2]);
         writes.append(newline);
         writes.append(lines[3]);
         inlineDeclarations.append(comma);
         inlineDeclarations.append(lines[4]);
         inlineInitializers.append(newline);
         inlineInitializers.append(lines[5]);
      }
      globalVals.add("field-declarations", declarations.toString());
      globalVals.add("field-defaults", defaults.toString());
      globalVals.add("field-writes", writes.toString());
      globalVals.add("field-reads", reads.toString());
      globalVals.add("inline-declarations", inlineDeclarations.toString());
      globalVals.add("inline-initializers", inlineInitializers.toString());
   }

   private void addConstantValues(List<Field> fields, TemplateValues globalVals) throws ParseException, IOException {
      globalVals.setSeperator("constants", "\n");
      for (Field f : fields) {
         if (!f.isConstant())
            continue;
         TemplateValues vals = getTemplateValues(f);
         String[] lines = template(vals.get("template")).expand(vals).split("\r\n|\n|\r");
         String comment = "";
         if (f.comment != null && !f.comment.trim().isEmpty()) {
            comment = generateComment(f.comment.trim());
         }
         globalVals.add("constants", comment + lines[0]);
      }
      globalVals.setIfEmpty("constants", "");
   }

   private TemplateValues getTemplateValues(Field f) {
      TemplateValues vals = new TemplateValues();
      JavaTypes.Info info = JavaTypes.get(f.type);
      vals.add("name", f.name);
      vals.add("javatype", info.base);
      vals.add("type", f.type);
      vals.add("boxed", info.boxed);
      String defaultVal = info.defaultValue;
      if (f.defaultValue != null) {
         if (f.type.equals("string"))
            f.defaultValue = escape(f.defaultValue);
         defaultVal = info.defaultValueDelim + f.defaultValue + info.defaultValueDelim;
      }
      vals.add("tag", f.tag);
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
      vals.add("template", (info.isPrimitive ? primTemplate : structTemplate));
      vals.add("default", defaultVal);
      return vals;
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
   
   private Templater template(String name) throws IOException {
      return Templater.get(getClass(), "javatemplates/" + name + ".template");
   }
}
