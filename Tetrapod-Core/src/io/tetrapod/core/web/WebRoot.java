package io.tetrapod.core.web;

import java.io.IOException;
import java.util.*;

public interface WebRoot {

   public static final Set<String> VALID_EXTENSIONS = new HashSet<>(Arrays.asList(new String[] { "html", "htm", "js", "css", "jpg", "png",
         "gif", "wav", "woff", "svg", "ttf", "swf" }));

   public static class FileResult {
      public String  path;
      public long    modificationTime;
      public byte[]  contents;
      public boolean doNotCache;
      public boolean isDirectory;
   }

   public void clear();

   public void addFile(String path, byte[] content);

   public FileResult getFile(String path) throws IOException;

   public Collection<String> getAllPaths();

   public int getMemoryFootprint();

}
