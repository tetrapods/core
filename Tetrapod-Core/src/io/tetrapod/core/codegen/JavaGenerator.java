package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGenContext.Class;
import io.tetrapod.core.codegen.CodeGenContext.Field;
import io.tetrapod.core.codegen.CodeGenContext.Group;

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
      for (Group g : context.groups) {
         for (Class c : g.classes) {
            generateClass(c);
         }
      }
   }
   
   private void generateClass(Class c) throws IOException,ParseException {
      Templater t = Templater.get(getClass(), "javatemplates/" + c.type.toLowerCase() + ".template");
      Map<String,String> vals = new HashMap<>();
      vals.put("class", c.classname());
      vals.put("package", packageName);
      // TODO: replace hash with tags?  or at least check for collisions
      vals.put("structid", ""+(FNVHash.hash32(c.classname()) & 0xffffff));
      addFieldValues(c.fields, vals);
      t.expand(vals, getFilename(c));
   }

   private void addFieldValues(List<Field> fields, Map<String,String> globalVals) throws ParseException, IOException {
      StringBuilder declarations = new StringBuilder();
      StringBuilder defaults = new StringBuilder();
      StringBuilder writes = new StringBuilder();
      StringBuilder reads = new StringBuilder();
      Field last = fields.size() > 0 ? fields.get(fields.size() - 1) : null;
      for (Field f : fields) {
         Map<String,String> vals = getTemplateValues(f);
         String[] lines = getTemplater(vals.get("template")).expand(vals).split("\r\n|\n|\r");
         declarations.append(lines[0]);
         if (f != last) 
            declarations.append('\n');
         defaults.append(lines[1]);
         if (f != last) 
            defaults.append('\n');
         reads.append(lines[2]);
         if (f != last) 
            reads.append('\n');
         writes.append(lines[3]);
         if (f != last) 
            writes.append('\n');
      }
      globalVals.put("field-declarations", declarations.toString());
      globalVals.put("field-defaults", defaults.toString());
      globalVals.put("field-writes", writes.toString());
      globalVals.put("field-reads", reads.toString());
   }

   private Map<String, String> getTemplateValues(Field f) {
      Map<String,String> vals = new HashMap<>();
      JavaTypes.Info info = JavaTypes.get(f.type);
      vals.put("name", f.name);
      vals.put("javatype", info.base);
      vals.put("type", f.type);
      vals.put("boxed", info.boxed);
      if (f.defaultValue != null)
         vals.put("default", info.defaultValueDelim + f.defaultValue + info.defaultValueDelim);
      else
         vals.put("default", info.defaultValue);
      vals.put("tag", f.tag);
      if (info.isPrimitive) {
         vals.put("template", "javatemplates/field.primitives.template");
      } else {
         vals.put("template", "javatemplates/field.structs.template");
      }
      return vals;
   }

   private Templater getTemplater(String template) throws IOException {
      return Templater.get(getClass(), template);
   }

   private File getFilename(Class c) {
      File f = new File(outputDir);
      for (String p : packageName.split("\\.")) {
         f = new File(f, p);
      }
      f.mkdirs();
      return new File(f, c.classname() + ".java");
   }
   
}
