package io.tetrapod.core.codegen;

import io.tetrapod.core.codegen.CodeGen.TokenizedLine;
import io.tetrapod.core.codegen.CodeGenContext.Class;

import java.io.*;
import java.util.*;

public class JavascriptGenerator implements LanguageGenerator {

   @Override
   public void parseOption(File f, TokenizedLine line, CodeGenContext context) throws ParseException {
      if (!line.parts.get(0).equals("javascript"))
         return;
      String opt = line.parts.get(1);
      String val = line.parts.get(2);
      switch (opt) {
         case "outdir":
            context.serviceAnnotations.add("javascript.outdir", new File(f.getParent(), val).getPath());
            break;
         default:
            throw new ParseException("unknown javascript option");
      }
   }

   @Override
   public void generate(List<CodeGenContext> contexts) throws IOException, ParseException {
      Set<String> outDirs = new HashSet<>();
      for (CodeGenContext context : contexts) {
         String o = context.serviceAnnotations.getFirst("javascript.outdir");
         if (o != null)
            outDirs.add(o);
      }
      for (String outdir : outDirs) {
         Template t = template("protocol.js");
         for (CodeGenContext context : contexts) {
            String out = context.serviceAnnotations.getFirst("javascript.outdir");
            if (out == null || !out.equals(outdir))
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
            }
         }
         File f = new File(outdir, "protocol.js");
         f.getParentFile().mkdirs();
         t.expandAndTrim(f);
      }
   }
   
   private Template template(String name) throws IOException {
      return Template.get(getClass(), "jstemplates/" + name + ".template");
   }

}
