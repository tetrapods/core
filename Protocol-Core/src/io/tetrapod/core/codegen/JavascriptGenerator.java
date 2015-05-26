package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.*;
import io.tetrapod.core.codegen.CodeGenContext.Class;

import java.io.*;
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
         t.add("name", outName);
         for (CodeGenContext context : contexts) {
            String out = context.serviceAnnotations.getFirst("javascript.out");
            if (out == null || !out.equals(outFile))
               continue;

            String contractName = context.serviceName;
            String contractId = context.serviceAnnotations.getFirst("id");
            for (Class c : context.classes) {
               Template sub = template("register");
               sub.add("contractClass", contractName);
               sub.add("class", c.name);
               sub.add("type", c.type);
               sub.add("contractId", contractId);
               sub.add("structId", c.getStructId());
               t.add("register", sub);
               for (Field f : c.fields) {
                  if (f.isConstant()) {
                     sub = template("register.const");
                     sub.add("contractClass", contractName);
                     sub.add("class", c.name);
                     sub.add("constName", f.name);
                     sub.add("constValue", f.getEscapedDefaultValue());
                     t.add("constants", sub);
                  }
               }
            }
            for (Field f : context.globalConstants) {
               if (f.isConstant()) {
                  Template sub = template("register.const");
                  sub.add("contractClass", contractName);
                  sub.add("class", "null");
                  sub.add("constName", f.name);
                  sub.add("constValue", f.getEscapedDefaultValue());
                  t.add("constants", sub);
               }
            }
            for (Err err : context.allErrors) {
               Template sub = template("register.const");
               sub.add("contractClass", contractName);
               sub.add("class", "null");
               sub.add("constName", err.name);
               sub.add("constValue", "" + err.getValue());
               t.add("errors", sub);
            }
         }
         file.getParentFile().mkdirs();
         t.expandAndTrim(file);
      }
   }

   private Template template(String name) throws IOException {
      return Template.get(getClass(), "/templates/jstemplates/" + name + ".template");
   }

}
