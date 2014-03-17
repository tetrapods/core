package io.tetrapod.core.codegen;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class CodeGen {

   public static void main(String[] args) {
      // just hardcode for now for testing
      args = new String[] { "definitions/Core.def", "java" };
      if (args.length < 1) {
         System.err.println("usage: arguments are filename lang1 lang2 ..");
      }
      CodeGen cg = new CodeGen();
      if (args.length == 1) {
         cg.run(args[0], "all");
         return;
      }
      for (int i = 1; i < args.length; i++) {
         cg.run(args[0], args[i]);
      }
   }

   private Map<String, LanguageGenerator> languages = new HashMap<>();
   private int                            currentLineNumber;
   private String                         currentLine;
   private CodeGenContext                 context;

   public void run(String filename, String language) {
      try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
         init(language);
         while (true) {
            currentLineNumber++;
            currentLine = br.readLine();
            if (currentLine == null)
               break;
            parse(currentLine);
         }
         flushAll();
      } catch (IOException | ParseException | IndexOutOfBoundsException e) {
         System.err.printf("Error in line #%d, [%s]\n\n", currentLineNumber, currentLine);
         e.printStackTrace();
      }
   }

   private void parse(String line) throws ParseException {
      ArrayList<String> parts = tokenize(line);
      if (parts == null || parts.size() == 0)
         return;
      combineTokens(parts);
      String key = parts.get(0);
      switch (key) {
         case "java":
         case "javascript":
         case "objc":
         case "c++":
            LanguageGenerator gen = languages.get(key);
            if (gen != null) {
               gen.parseOption(parts);
            }
            return;

         case "service":
            context.parseService(parts);
            break;
            
         case "default":
            String t = parts.get(1);
            if (t.equals("security")) {
               context.defaultSecurity = parts.get(2);
            }
            break;
            
         case "request":
         case "response":
         case "message":
         case "struct":
         case "public":
         case "protected":
         case "internal":
         case "admin":
            context.parseClass(parts);
            break;

         case "field":
            context.parseField(parts);
            break;
            
         case "const":
            parts.set(0, "field");
            parts.add(1, "0");
            context.parseField(parts);
            break;
            
         case "error":
            context.parseErrors(parts);
            break;
            
         case "global":
            if (parts.get(1).equals("scope")) {
               context.inGlobalScope = true;
            } else {
               throw new ParseException("malformaed global scope line");
            }
            return;
            
         default:
            throw new ParseException("unknown key [" + key + "]");
      }
   }

   private static ArrayList<String> tokenize(String line) {
      ArrayList<String> tokens = new ArrayList<String>();
      // drop comments
      int ix = line.indexOf("//");
      if (ix >= 0)
         line = line.substring(0, ix);
      // divide into words
      String[] clauses = { "([/\\w.-]+)", "([\\[\\]<>:={}])", "\"([^\"]+)\"" };
      String regex = clauses[0];
      for (int i = 1; i < clauses.length; i++)
         regex = regex + "|" + clauses[i];
      Matcher m = Pattern.compile(regex).matcher(line);
      while (m.find()) {
         for (int i = 1; i <= clauses.length; i++) {
            String token = m.group(i);
            if (m.group(i) != null)
               tokens.add(token);
         }
      }
      return tokens;
   }
   
   private static void combineTokens(ArrayList<String> parts) {
      if (parts.get(0).equals(":")) {
         parts.remove(0);
         parts.add(0, "tag");
      }
      if (parts.size() > 1 && parts.get(1).equals(":")) {
         parts.remove(1);
         parts.add(0, "field");
      }
      for (int i=0; i < parts.size() - 1; i++) {
         if (parts.get(i).equals("=") && parts.get(i+1).equals(">")) {
            parts.set(i, "<maps>");
            parts.remove(i+1);
         }
         if (parts.get(i).equals("<") && parts.get(i+2).equals(">")) {
            parts.set(i, "<" + parts.get(i+1) + ">");
            parts.remove(i+1);
            parts.remove(i+1);
         }
         if (parts.get(i).equals("[") && parts.get(i+1).equals("]")) {
            parts.set(i, "<array>");
            parts.remove(i+1);
         }
         if (parts.get(i).equals("{") && parts.get(i+1).equals("}")) {
            parts.set(i, "<empty>");
            parts.remove(i+1);
         }
      }
   }

   private void flushAll() throws IOException, ParseException {
      for (LanguageGenerator gen : languages.values()) {
         gen.generate(context);
      }
      // System.out.println(context.toString());
   }

   private void init(String language) throws ParseException {
      context = new CodeGenContext();
      currentLine = null;
      currentLineNumber = 0;
      switch (language) {
         case "java":
            languages.put("java", new JavaGenerator());
            break;
         default:
            throw new ParseException("unknowm language: " + language);
      }
   }

   static String toTitleCase(String s) {
      switch (s.length()) {
         case 0:
            return s;
         case 1:
            return s.toUpperCase();
         default:
            return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
      }
   }

}
