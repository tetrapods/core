package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.*;
import io.tetrapod.core.codegen.CodeGenContext.Class;

import java.io.*;
import java.util.*;

class ObjCWebSocketsGenerator implements LanguageGenerator {
   
   @Override
   public void parseOption(File f, TokenizedLine line, CodeGenContext context) throws ParseException {
      if (!line.parts.get(0).equals("objc"))
         return;
      String opt = line.parts.get(1);
      String val = line.parts.get(2);
      switch (opt) {
         case "outdir":
            context.serviceAnnotations.add("objc.outdir", new File(f.getParent(), val).getPath());
            break;
         default:
            throw new ParseException("unknown objc option");
      }
   } 
   
   @Override
   public void generate(List<CodeGenContext> contexts) throws IOException, ParseException {
      Set<String> outFiles = new HashSet<>();
      for (CodeGenContext context : contexts) {
         String o = context.serviceAnnotations.getFirst("objc.outdir");
         if (o != null)
            outFiles.add(o);
      }
      // Interface
      for (String outFile : outFiles) {
         Template t = template("protocol.h");
         File file = new File(outFile);
         String contractName = "";
         for (CodeGenContext context : contexts) {
            String out = context.serviceAnnotations.getFirst("objc.outdir");
            if (out == null || !out.equals(outFile)) {
               continue;
            }
            contractName = context.serviceName;
            t.add("name", contractName);
            t.add("contractClass", contractName);
            for (Class c : context.classes) {
               for (Field f : c.fields) {
                  if (f.isConstant()) {
                     Template sub = template("defintconst");
                     sub.add("contractClass", contractName);
                     sub.add("constName", f.name);
                     t.add("defErrors", sub);
                  }
               }
            }
            for (Field f : context.globalConstants) {
               if (f.isConstant()) {
                  Template sub;
                  if (f.type.equals("string")) {
                     sub = template("defstringconst");
                  } else {
                     sub = template("defintconst"); 
                  }
                  sub.add("contractClass", contractName);
                  sub.add("constName", f.name);
                  t.add("defConsts", sub);
               }
            }
            for (Err err : context.allErrors) {
               Template sub = template("defintconst");
               sub.add("contractClass", contractName + "_ERROR");
               sub.add("constName", err.name);
               t.add("defErrors", sub);
            }
         }
         file = new File(outFile, contractName + "Protocol.h");
         file.getParentFile().mkdirs();
         t.expandAndTrim(file);
      }
      // Implementation
      for (String outFile : outFiles) {
         Template t = template("protocol.m");
         File file = new File(outFile);         
         String contractName = "";
         for (CodeGenContext context : contexts) {
            String out = context.serviceAnnotations.getFirst("objc.outdir");
            if (out == null || !out.equals(outFile)) {
               continue;
            }
            contractName = context.serviceName;
            t.add("name", contractName);
            t.add("contractClass", contractName);
            String contractId = context.serviceAnnotations.getFirst("id");
            for (Class c : context.classes) {
               Template sub = template("addtype");
               sub.add("contractClass", contractName);
               sub.add("class", c.name);
               sub.add("type", c.type);
               sub.add("contractId", contractId);
               sub.add("structId", c.getStructId());
               t.add("register", sub);
               for (Field f : c.fields) {
                  if (f.isConstant()) {
                     if (f.type.equals("string")) {
                        sub = template("stringconst");
                     } else {
                        sub = template("intconst"); 
                     }
                     sub.add("contractClass", contractName);
                     sub.add("class", c.name);
                     sub.add("constName", f.name);
                     sub.add("constValue", f.getEscapedDefaultValue());
                     t.add("consts", sub);
                  }
               }
            }
            for (Field f : context.globalConstants) {
               if (f.isConstant()) {
                  Template sub;
                  if (f.type.equals("string")) {
                     sub = template("stringconst");
                  } else {
                     sub = template("intconst"); 
                  }
                  sub.add("contractClass", contractName);
                  sub.add("constName", f.name);
                  sub.add("constValue", f.getEscapedDefaultValue());
                  t.add("consts", sub);
               }
            }
            for (Err err : context.allErrors) {
               Template sub = template("intconst");
               sub.add("contractClass", contractName + "_ERROR");
               sub.add("constName", err.name);
               sub.add("constValue", "" + err.getValue());
               t.add("errors", sub);
            }
         }
         file = new File(outFile, contractName + "Protocol.m");
         file.getParentFile().mkdirs();
         t.expandAndTrim(file);
      }
   }

   private Template template(String name) throws IOException {
      return Template.get(getClass(), "objctemplates/" + name + ".template");
   }

}
