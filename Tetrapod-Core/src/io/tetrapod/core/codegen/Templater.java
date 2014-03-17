package io.tetrapod.core.codegen;

import java.io.*;
import java.util.*;

/**
 * Simple templating system.  Templates can have {{key}} in them.  Upon expanding the keys
 * are looked up in a passed in map and replaced.  Templates optionally memoized (on by default).
 * <p>
 * 
 * If a value has embedded new lines in it, each line will be indented so that it lines up with
 * the start of the {{key}} in the the template.
 * <p>
 * 
 * Opening but not closing a {{key}} throws a parse error as an IOException.
 * 
 * @author fortin
 */
public class Templater {
   
   public static boolean MEMOIZE = true;
   private static Map<String,Templater> saved = new HashMap<>();

   public static Templater get(File file) throws IOException {
      try (BufferedReader br = new BufferedReader(new FileReader(file))) {
         return get(br, file.getPath());
      }
   }
   
   public static Templater get(Class<?> context, String resourceName) throws IOException {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getResourceAsStream(resourceName)))) {
         return get(br, context.getName() + "::" + resourceName);
      }
   }

   public static Templater get(Reader r, String key) throws IOException {
      if (MEMOIZE && key != null) {
         Templater t = saved.get(key);
         if (t != null)
            return t;
      }
      Templater res = new Templater().load(r);
      if (MEMOIZE && key != null) {
         saved.put(key, res);
      }
      return res;
   }
   

   private char[] chars;

   private Templater load(Reader r) throws IOException {
      CharArrayWriter w = new CharArrayWriter();
      while (true) {
         int c = r.read();
         if (c < 0)
            break;
         w.write(c);
      }
      chars = w.toCharArray();
      return this;
   }

   public String expand(Map<String, String> values) throws IOException {
      StringWriter sw = new StringWriter();
      expand(values, sw);
      return sw.toString();
   }

   public void expand(Map<String, String> values, File file) throws IOException {
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
         expand(values, bw);
      }
   }

   /**
    * Expand, getting rid of consecutive whitespace-only lines.
    */
   public void expandAndTrim(Map<String, String> values, File file) throws IOException {
      String s = expand(values);
      String[] lines = s.split("\n|\r\n");
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
         boolean prevWasWhitespace = false;
         for (String line : lines) {
            boolean isWhitespace = line.trim().length() == 0;
            if (!prevWasWhitespace || !isWhitespace) {
               bw.append(line);
               bw.append("\n");
            }
            prevWasWhitespace = isWhitespace;
         }
      }
   }

   public void expand(Map<String, String> values, Writer out) throws IOException {
      int indent = 0;
      int n = chars.length;
      for (int i = 0; i < n; i++) {
         if (chars[i] == '{' && i < n - 4 && chars[i + 1] == '{') {
            i = doExpand(i + 2, values, indent, out);
         } else {
            out.write(chars[i]);
            indent++;
            if (chars[i] == '\n') {
               indent = 0;
            }
         }
      }
   }

   private int doExpand(int startIx, Map<String, String> values, int indent, Writer out) throws IOException {
      int endIx = startIx + 1;
      while (endIx < chars.length && chars[endIx] != '}')
         endIx++;
      if (endIx + 1 >= chars.length || chars[endIx + 1] != '}')
         throw new IOException("unclosed templated string");
      String key = new String(chars, startIx, endIx - startIx);
      String val = values.get(key);
      if (val == null) {
         out.append("{{" + key + "}}");
      } else {
         String[] lines = val.split("\n");
         out.append(lines[0]);
         for (int k = 1; k < lines.length; k++) {
            out.write('\n');
            for (int ii = 0; ii < indent; ii++) {
               out.write(' ');
            }
            out.write(lines[k]);
         }
      }
      return endIx + 1;
   }

}
