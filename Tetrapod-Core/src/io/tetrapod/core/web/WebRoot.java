package io.tetrapod.core.web;

import java.io.IOException;
import java.util.Collection;

public interface WebRoot {
   
   public static class FileResult {
      public String path;
      public long modificationTime;
      public byte[] contents;
      public boolean isIndex;
      public boolean isDirectory;
   }

   public void clear();

   public void addFile(String path, byte[] content);

   public FileResult getFile(String path) throws IOException;

   public Collection<String> getAllPaths();

   public int getMemoryFootprint();

}
