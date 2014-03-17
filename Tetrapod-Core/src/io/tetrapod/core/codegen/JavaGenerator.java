package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGenContext.Class;
import io.tetrapod.core.codegen.CodeGenContext.Field;

import java.io.*;
import java.util.*;

class JavaGenerator implements LanguageGenerator {

   private String packageName;
   private String outputDir;
   
   public void parseOption(List<String> components) throws ParseException {
//      java package io.tetrapod.identity.protocol
//      java outdir src
      String opt = components.get(1);
      String val = components.get(2);
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
      Map<String,StringBuilder> subscriptions = new HashMap<>();
      StringBuilder sb = new StringBuilder();
      for (Class c : context.classes) {
         generateClass(c, context.serviceName + "ServiceAPI");
         addHandlerLine(c, sb, subscriptions);
      }
      generateService(context.serviceName, context.serviceVersion, context.allErrors, sb.toString());
      for (String sub : subscriptions.keySet()) {
         generateSubscription(sub, subscriptions.get(sub).toString());
      }
   }
   
   private void generateService(String serviceName, String serviceVersion, Collection<String> allErrors, String handlers) throws IOException,ParseException {
      Templater t = Templater.get(getClass(), "javatemplates/servicerpc.template");
      Map<String,String> vals = new HashMap<>();
      vals.put("class", serviceName + "ServiceAPI");
      vals.put("package", packageName);
      vals.put("version", serviceVersion);
      vals.put("handlers", handlers);
      addErrors(allErrors, true, serviceName, vals);
      t.expandAndTrim(vals, getFilename(vals.get("class")));
   }

   private void generateSubscription(String name, String handlers) throws IOException,ParseException {
      Templater t = Templater.get(getClass(), "javatemplates/subscription.template");
      Map<String,String> vals = new HashMap<>();
      vals.put("class", name + "Subscription");
      vals.put("package", packageName);
      vals.put("handlers", handlers);
      t.expandAndTrim(vals, getFilename(vals.get("class")));
   }

   
   private void generateClass(Class c, String serviceName) throws IOException,ParseException {
      Templater t = Templater.get(getClass(), "javatemplates/" + c.type.toLowerCase() + ".template");
      Map<String,String> vals = new HashMap<>();
      vals.put("rawname", c.name);
      vals.put("class", c.classname());
      vals.put("package", packageName);
      vals.put("security", c.security.toUpperCase());
      if (c.structId == null) {
         // auto-genned hashes are never less than 10
         c.structId = "" + ((FNVHash.hash32(c.classname()) & 0xffffff) + 10);
      }
      vals.put("structid", c.structId);
      addFieldValues(c.fields, vals);
      addConstantValues(c.fields, vals);
      addErrors(c.errors, false, serviceName, vals);
      t.expandAndTrim(vals, getFilename(c.classname()));
   }

   private void addErrors(Collection<String> errors, boolean globalScope, String serviceName, Map<String, String> vals) throws IOException,ParseException {
      StringBuilder sb = new StringBuilder();
      boolean isFirst = true;
      for (String err : errors) {
         Map<String,String> v = new HashMap<>();
         v.put("name", err);
         // error hashes are never less than 100
         v.put("hash", ""+ ((FNVHash.hash32(err) & 0xffffff) + 100));
         v.put("service", serviceName);
         String[] lines = getTemplater("javatemplates/field.errors.template").expand(v).split("\r\n|\n|\r");
         String line = globalScope ? lines[0] : lines[1];
         String newline = "\n";
         if (isFirst) {
            isFirst = false;
            newline = "";
         }
         sb.append(newline);
         sb.append(line);
      }
      vals.put("errors", sb.toString());
   }

   private void addFieldValues(List<Field> fields, Map<String,String> globalVals) throws ParseException, IOException {
      StringBuilder declarations = new StringBuilder();
      StringBuilder defaults = new StringBuilder();
      StringBuilder writes = new StringBuilder();
      StringBuilder reads = new StringBuilder();
      boolean isFirst = true;
      for (Field f : fields) {
         if (f.isConstant())
            continue;
         Map<String,String> vals = getTemplateValues(f);
         String[] lines = getTemplater(vals.get("template")).expand(vals).split("\r\n|\n|\r");
         String newline = "\n";
         if (isFirst) {
            isFirst = false;
            newline = "";
         }
         declarations.append(newline);
         declarations.append(lines[0]);
         defaults.append(newline);
         defaults.append(lines[1]);
         reads.append(newline);
         reads.append(lines[2]);
         writes.append(newline);
         writes.append(lines[3]);
      }
      globalVals.put("field-declarations", declarations.toString());
      globalVals.put("field-defaults", defaults.toString());
      globalVals.put("field-writes", writes.toString());
      globalVals.put("field-reads", reads.toString());
   }

   private void addConstantValues(List<Field> fields, Map<String,String> globalVals) throws ParseException, IOException {
      StringBuilder declarations = new StringBuilder();
      boolean isFirst = true;
      for (Field f : fields) {
         if (!f.isConstant())
            continue;
         Map<String,String> vals = getTemplateValues(f);
         String[] lines = getTemplater(vals.get("template")).expand(vals).split("\r\n|\n|\r");
         String newline = "\n";
         if (isFirst) {
            isFirst = false;
            newline = "";
         }
         declarations.append(newline);
         declarations.append(lines[0]);
      }
      globalVals.put("constants", declarations.toString());
   }

   private Map<String, String> getTemplateValues(Field f) {
      Map<String,String> vals = new HashMap<>();
      JavaTypes.Info info = JavaTypes.get(f.type);
      vals.put("name", f.name);
      vals.put("javatype", info.base);
      vals.put("type", f.type);
      vals.put("boxed", info.boxed);
      String defaultVal = info.defaultValue;
      if (f.defaultValue != null)
         defaultVal = info.defaultValueDelim + f.defaultValue + info.defaultValueDelim;
      vals.put("tag", f.tag);
      String primTemplate = "field.primitives.template";
      String structTemplate = "field.structs.template";
      if (f.collectionType != null) {
         defaultVal = "null";
         boolean isEmpty = f.defaultValue != null && f.defaultValue.equals("<empty>");
         switch (f.collectionType) {
            case "<array>":
               primTemplate = "field.array.primitives.template";
               structTemplate = "field.array.structs.template";
               if (isEmpty)
                  defaultVal = "new " + info.base + "[0]";
               break;
            case "<list>":
               primTemplate = "field.list.primitives.template";
               structTemplate = "field.list.structs.template";
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
         primTemplate = structTemplate = "field.constants.template";
      }
      vals.put("template", "javatemplates/" + (info.isPrimitive ? primTemplate : structTemplate));
      vals.put("default", defaultVal);
      return vals;
   }

   private Templater getTemplater(String template) throws IOException {
      return Templater.get(getClass(), template);
   }

   private File getFilename(String classname) {
      File f = new File(outputDir);
      for (String p : packageName.split("\\.")) {
         f = new File(f, p);
      }
      f.mkdirs();
      return new File(f, classname + ".java");
   }
   
   private void addHandlerLine(Class c, StringBuilder sb, Map<String, StringBuilder> subscriptions) {
      if (c.type.equals("request")) {
         if (sb.length() > 0) sb.append(",\n");
         sb.append(c.classname());
         sb.append(".Handler");
      }
      if (c.type.equals("message")) {
         String sub = c.subscription;
         if (sub == null || sub.isEmpty())
            return;
         StringBuilder s = subscriptions.get(sub);
         if (s == null) {
            s = new StringBuilder();
            subscriptions.put(sub, s);
         }
         if (s.length() > 0) s.append(",\n");
         s.append(c.classname());
         s.append(".Handler");
      }
   }

   
}
