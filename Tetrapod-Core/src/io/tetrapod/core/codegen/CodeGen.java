package io.tetrapod.core.codegen;

import java.io.*;
import java.util.*;

/**
 * Main class for code generation.  Does the low level tokeninzing and then passes
 * off to a CodeGenContext class that does more of a semantic parse and stores the
 * result of the parse. Then it passes that to a LanguageGenerator to write the 
 * final language specific files.
 */
public class CodeGen {

   public static void main(String[] args) {
      //if (true) { testTokenize();return; }
      // just hardcode for now for testing
      args = new String[] { "definitions", "java" };
      if (args.length < 1) {
         System.err.println("usage: arguments are filename lang1 lang2 ...");
         System.err.println("       if filename is a folder it will recursively run on all the files");
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
   
   protected static class TokenizedLine {
      ArrayList<String> parts = new ArrayList<>();
      String comment;
      Annotations annotations = new Annotations();
      
      public boolean isEmpty() { return parts.isEmpty(); }
      public String key() { return parts.get(0); }
      
      public void clear() {
         parts.clear();
         comment = null;
         annotations = new Annotations();
      }
   }

   private LanguageGenerator              generator;
   private int                            currentLineNumber;
   private String                         currentLine;
   private CodeGenContext                 context;
   private TokenizedLine                  tokenizedLine = new TokenizedLine();
   private StringBuilder                  commentInProgress;

   public void run(String filename, String language) {
      ArrayList<File> files = new ArrayList<>();
      files.add(new File(filename));
      int ix = 0;
      
      while (ix < files.size()) {
         File file = files.get(ix);
         if (file.isDirectory()) {
            files.addAll(Arrays.asList(file.listFiles()));
         } else {
            runFile(file, language);
         }
         ix++;
      }
   }
   
   private void runFile(File f, String language) {
      System.out.println("Generating " + f.getName() + " for " + language);
      try (BufferedReader br = new BufferedReader(new FileReader(f))) {
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
      tokenizedLine.clear();
      tokenize(line, tokenizedLine);
      combineTokens(tokenizedLine);
      if (tokenizedLine.comment != null) {
         commentInProgress.append(tokenizedLine.comment);
         commentInProgress.append(" ");
      }
      if (tokenizedLine.isEmpty()) {
         return;
      }
      tokenizedLine.comment = commentInProgress.toString();
      commentInProgress = new StringBuilder();
      String key = tokenizedLine.key();
      switch (key) {
         case "java":
         case "javascript":
         case "objc":
         case "c++":
            generator.parseOption(tokenizedLine);
            return;

         case "service":
            context.parseService(tokenizedLine);
            break;
            
         case "default":
            String t = tokenizedLine.parts.get(1);
            if (t.equals("security")) {
               context.defaultSecurity = tokenizedLine.parts.get(2);
            }
            break;
            
         case "request":
         case "response":
         case "message":
         case "struct":
         case "public":
         case "private":
         case "protected":
         case "internal":
         case "admin":
            context.parseClass(tokenizedLine);
            break;

         case "field":
            context.parseField(tokenizedLine);
            break;
            
         case "const":
            tokenizedLine.parts.set(0, "field");
            tokenizedLine.parts.add(1, "0");
            context.parseField(tokenizedLine);
            break;
            
         case "error":
            context.parseErrors(tokenizedLine);
            break;
            
         case "global":
            if (tokenizedLine.parts.get(1).equals("scope")) {
               context.inGlobalScope = true;
            } else {
               throw new ParseException("malformaed global scope line");
            }
            return;
            
         default:
            throw new ParseException("unknown key [" + key + "]");
      }
   }

   private static void combineTokens(TokenizedLine tokenizedLine) {
      ArrayList<String> parts = tokenizedLine.parts;
      for (int i=0; i < parts.size() - 1; i++) {
         if (i==1 && parts.get(i).equals(":")) {
            parts.remove(i);
            parts.add(0, "field");
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
         if (parts.get(i).equals("@")) {
            parts.remove(i);
            String tag = parts.remove(i);
            if (i >= parts.size() || !parts.get(i).equals("(")) {
               i--;
               tokenizedLine.annotations.add(tag, "");
            } else {
               parts.remove(i);
               while (true) {
                  String val = parts.remove(i);
                  if (val.equals(")")) {
                     i--;
                     break;
                  }
                  if (!val.equals(","))
                     tokenizedLine.annotations.add(tag, val);
               }
            }
         }
      }
   }

   private void flushAll() throws IOException, ParseException {
      generator.generate(context);
   }

   private void init(String language) throws ParseException {
      context = new CodeGenContext();
      currentLine = null;
      currentLineNumber = 0;
      commentInProgress = new StringBuilder();
      switch (language) {
         case "java":
            generator = new JavaGenerator();
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
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
      }
   }
   
   private static enum TokenizeStates { OUT_WORD, IN_WORD, IN_STRING };
   
   private static void tokenize(String line, TokenizedLine out) {
      TokenizeStates state = TokenizeStates.OUT_WORD;
      StringBuilder word = null;
      int i = 0;
      int N = line.length();
      while (i <= N) {
         char c = i == N ? 0 : line.charAt(i);
         char peek = i >= N-1 ? 0 : line.charAt(i+1);
         switch (state) {
            case OUT_WORD:
               if (c == '#') {
                  return;
               }
               if (c == '/' && peek == '/') {
                  out.comment = line.substring(i+2).trim();
                  return;
               }
               if (isWord(c) || isDivider(c)) {
                  state = TokenizeStates.IN_WORD;
                  word = new StringBuilder();
                  break;
               }
               if (c == '"') {
                  i++;
                  state = TokenizeStates.IN_STRING;
                  word = new StringBuilder();
                  break;
               }
               // all other characters skipped
               i++;
               break;
            case IN_WORD:
               if (isWord(c)) {
                  word.append(c);
                  i++;
                  break;
               }
               if (word.length() > 0) {
                  out.parts.add(word.toString());
                  word = new StringBuilder();
               }
               if (isDivider(c)) {
                  i++;
                  out.parts.add("" + c);
               }
               state = TokenizeStates.OUT_WORD;
               break;
            case IN_STRING:
               if (c == '"') {
                  i++;
                  if (word.length() > 0) {
                     out.parts.add(word.toString());
                     word = new StringBuilder();
                  }
                  state = TokenizeStates.OUT_WORD;
                  break;
               }
               if (c == '\\' && peek > 0) {
                  i++;
                  c = peek;
               }
               i++;
               word.append(c);
               break;
         }
      }
   }

   private static boolean isWord(char c) {
      if (Character.isAlphabetic(c)) return true;
      if (Character.isDigit(c)) return true;
      return c == '_' || c == '.' || c == '-';
   }

   private static boolean isDivider(char c) {
      return "[]<>:={}()@,".indexOf(c) >= 0;
   }
   
   @SuppressWarnings("unused")
   private static void testTokenize() {
      TokenizedLine out = new TokenizedLine();
      tokenize("1: 1th-is1[] <i.s> @web @id(33334,5) @x(2,3,\"44,55\",66,) // _a \"# te\\\\\\\"st\"", out);
      combineTokens(out);
      System.out.println(out.parts);
      System.out.println(out.annotations);
      System.out.println(out.comment);
   }
   

}
