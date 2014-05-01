package io.tetrapod.core.codegen;

import java.io.*;
import java.util.*;

/**
 * Simple templating system.  Templates can have {{key}} in them.  Upon expanding the keys
 * are looked up in a passed in map and replaced.  Templates optionally memoized (on by default).
 * <p>
 * If a value has embedded new lines in it, each line will be indented so that it lines up with
 * the start of the {{key}} in the the template.
 * <p>
 * Opening but not closing a {{key}} throws a parse error as an IOException.  Keys which are have
 * no value to expand are left as-is in the output.
 */
public class Template {
   
   private static class TemplateData {
      private final char[] data;
   
      public TemplateData(char[] data) {
         this.data = data;
      }
   }
   
   private static class Values {
      List<String> values = new ArrayList<>();
      String seperator = "\n";

      public void write(Writer w, int indent) throws IOException {
         append(values.get(0), w, indent);
         for (int i = 1; i < values.size(); i++) {
            append(seperator, w, indent);
            append(values.get(i), w, indent);
         }
      }
      
      private void append(String s, Writer w, int indent) throws IOException {
         int N = s.length();
         for (int i = 0; i < N; i++) {
            char c = s.charAt(i);
            w.append(c);
            if (c == '\n') {
               for (int j = 0; j < indent; j++)
                  w.append(' ');
            }
         }
      }
   }
   
   // simple cache
   
   public static boolean MEMOIZE = true;
   private static Map<String,TemplateData> saved = new HashMap<>();

   public static Template get(File file) throws IOException {
      try (BufferedReader br = new BufferedReader(new FileReader(file))) {
         return get(br, file.getPath());
      }
   }
   
   public static Template get(Class<?> context, String resourceName) throws IOException {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getResourceAsStream(resourceName)))) {
         return get(br, context.getName() + "::" + resourceName);
      }
   }

   public static Template get(Reader r, String key) throws IOException {
      if (MEMOIZE && key != null) {
         TemplateData t = saved.get(key);
         if (t != null)
            return new Template(t);
      }
      Template res = new Template(null).load(r);
      if (MEMOIZE && key != null) {
         saved.put(key, res.data);
      }
      return res;
   }
   
   // instance implementations
   

   private TemplateData data;
   private Map<String, Values> map = new HashMap<>();
   
   public Template(TemplateData data) {
      this.data = data;
   }
   
   private Template load(Reader r) throws IOException {
      CharArrayWriter w = new CharArrayWriter();
      while (true) {
         int c = r.read();
         if (c < 0)
            break;
         w.write(c);
      }
      data = new TemplateData(w.toCharArray());
      return this;
   }

   public String expand() {
      StringWriter sw = new StringWriter();
      try {
         expand(sw);
      } catch (IOException e) {}
      return sw.toString();
   }

   public void expand(File file) throws IOException {
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
         expand(bw);
      }
   }

   /**
    * Expand, getting rid of consecutive whitespace-only lines.
    */
   public void expandAndTrim(File file) throws IOException {
      String s = expand();
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

   public void expand(Writer out) throws IOException {
      char[] chars = data.data;
      int indent = 0;
      int n = chars.length;
      for (int i = 0; i < n; i++) {
         if (chars[i] == '{' && i < n - 4 && chars[i + 1] == '{') {
            i = doExpand(i + 2, indent, out);
         } else {
            out.write(chars[i]);
            indent++;
            if (chars[i] == '\n') {
               indent = 0;
            }
         }
      }
   }

   private int doExpand(int startIx, int indent, Writer out) throws IOException {
      char[] chars = data.data;
      int endIx = startIx + 1;
      while (endIx < chars.length && chars[endIx] != '}')
         endIx++;
      if (endIx + 1 >= chars.length || chars[endIx + 1] != '}')
         throw new IOException("unclosed templated string");
      String key = new String(chars, startIx, endIx - startIx);
      if (map.containsKey(key)) {
         map.get(key).write(out, indent);
      } else {
         // quietly drop
         // TODO make this an option
         // out.append("{{" + key + "}}");
      }
      return endIx + 1;
   }
   
   public Template add(String key, String val) {
      return add(key, val, "\n");
   }
   
   public Template add(String key, Template val) {
      return add(key, val.expand(), "\n");
   }
   
   public Template add(String key, String val, String sep) {
      Values v = map.get(key);
      if (v == null) {
         v = new Values();
         v.seperator = sep;
         map.put(key, v);
      }
      if (sep.length() > 0)
         v.seperator = sep;
      v.values.add(val);
      return this;
   }

   public Template add(Template t) {
      map.putAll(t.map);
      return this;
   }

}
