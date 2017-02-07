package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.*;
import io.tetrapod.core.codegen.CodeGenContext.Class;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class JavascriptGenerator implements LanguageGenerator {

   @Override
   public void parseOption(File f, TokenizedLine line, CodeGenContext context) throws ParseException {
      if (!line.parts.get(0).equals("javascript"))
         return;
      String opt = line.parts.get(1);
      String val = line.parts.get(2);
      switch (opt) {
         case "out":
            context.serviceAnnotations.add("javascript.out", new File(f.getParent(), val).getPath());
            break;
         case "altOut":
            String jsOut = context.serviceAnnotations.getFirst("javascript.out");
            if (context.altParents != null && !context.altParents.isEmpty() && jsOut != null) {
               for (String altParent : context.altParents) {
                  File srcDir = new File(jsOut).toPath().normalize().toFile().getParentFile();
                  File destDir = new File(altParent);
                  for (String s : val.split(",")) {
                     context.serviceAnnotations.add("javascript.altOut", new File(srcDir, s).getPath());
                     context.serviceAnnotations.add("javascript.altOut", new File(destDir, s).getPath());
                  }
               }
            }
         break;
            
         default:
            throw new ParseException("unknown javascript option");
      }
   }

   @Override
   public void generate(List<CodeGenContext> contexts) throws IOException, ParseException {
      Set<String> outFiles = new HashSet<>();
      for (CodeGenContext context : contexts) {
         String o = context.serviceAnnotations.getFirst("javascript.out");
         if (o != null)
            outFiles.add(o);
      }
      for (String outFile : outFiles) {
         Template t = template("protocol.js");
         File file = new File(outFile);
         System.out.println("Writing " + file.getAbsolutePath());
         String outName = file.getName();
         int ix = outName.indexOf('.');
         outName = outName.substring(0, 1).toUpperCase() + outName.substring(1, ix);
         outName = outName.replaceAll("-", "_");
         t.add("name", outName);
         for (CodeGenContext context : contexts) {
            String out = context.serviceAnnotations.getFirst("javascript.out");
            if (out == null || !out.equals(outFile))
               continue;

            String contractName = context.serviceName;
            t.add("contractName", contractName);
            String contractId = context.serviceAnnotations.getFirst("id");
            t.add("contractId", contractId);
            String subContractId = context.serviceAnnotations.getFirst("subId");
            if (subContractId == null) {
               subContractId = "1";
            }
            for (Class c : context.classes) {
               Template sub = template("register");
               sub.add("contractClass", contractName);
               sub.add("class", c.name);
               sub.add("type", c.type);
               sub.add("contractId", contractId);
               sub.add("subContractId", subContractId);
               sub.add("structId", c.getStructId());
               String routedQualifier = c.getRoutedQualifier();
               if (routedQualifier != null) {
                  sub.add("routerParams", "\""+ c.getRoutedField().name + "\", \"" + routedQualifier +"\"");
               } else {
                  sub.add("routerParams", "null, null");
               }
               t.add("register", sub);
               TreeMap<String, String> fields = new TreeMap<>();
               for (Field f : c.fields) {
                  if (f.isConstant()) {
                     fields.put(f.name, f.getEscapedDefaultValue());
                  }
               }
               if (fields.size() > 0) {
                  Template enumTemplate = template("enum");
                  enumTemplate.add("contractClass", contractName);
                  enumTemplate.add("class", c.name);
                  Iterator<Map.Entry<String, String>> fieldIterator = fields.entrySet().iterator();
                  while (fieldIterator.hasNext()) {
                     Map.Entry<String, String> field = fieldIterator.next();
                     Template tf = template("field.enum");
                     tf.add("constName", field.getKey());
                     tf.add("constValue", field.getValue());
                     String line = tf.expand().split("\r\n|\n|\r")[0];
                     enumTemplate.add("constants", line + (fieldIterator.hasNext() ? "," : ""));
                  }
                  t.add("constants", enumTemplate);
               }
            }
            for (Field f : context.globalConstants) {
               if (f.isConstant()) {
                  Template sub = template("namespace.const");
                  sub.add("constType", contractName);
                  sub.add("constName", f.name);
                  sub.add("constValue", f.getEscapedDefaultValue());
                  t.add("constants", sub);
               }
            }
            for (ClassLike c : context.enums.values()) {
               Template sub = template("enum");
               sub.add("contractClass", contractName);
               sub.add("class", c.name);
               addFields(c, sub);
               t.add("constants", sub);
            }
            for (ClassLike c : context.flags.values()) {
               Template sub = template("flag");
               sub.add("contractClass", contractName);
               sub.add("class", c.name);
               addFields(c, sub);
               t.add("constants", sub);
            }
            for (Err err : context.allErrors) {
               Template sub = template("namespace.const");
               sub.add("constType", contractName + ".error");
               sub.add("constName", err.name);
               sub.add("constValue", "" + err.getValue());
               t.add("errors", sub);
            }
         }
         file.getParentFile().mkdirs();
         t.expandAndTrim(file);
      }
      for (CodeGenContext context : contexts) {
         List<String> alts = context.serviceAnnotations.get("javascript.altOut");
         if (alts != null) {
            for (int i = 0; i < alts.size(); i += 2) {
               String src = alts.get(i);
               String dest = alts.get(i+1);
               Files.copy(Paths.get(src), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   private void addFields(ClassLike c, Template sub) throws IOException {
      Iterator<Field> fieldIterator = c.fields.iterator();
      while (fieldIterator.hasNext()) {
         Field f = fieldIterator.next();
         Template tf = template("field.enum");
         tf.add("constName", f.name);
         tf.add("constValue", f.getEscapedDefaultValue());
         String line = tf.expand().split("\r\n|\n|\r")[0];
         sub.add("constants", line + (fieldIterator.hasNext() ? "," : ""));
      }
   }

   private Template template(String name) throws IOException {
      return Template.get(getClass(), "/templates/jstemplates/" + name + ".template");
   }

}
