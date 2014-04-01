package io.tetrapod.core.kvstore;


import io.tetrapod.core.StructureFactory;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.core.serialize.datasources.FileDataSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Simple key value store.  Each store has a name which needs to be unique from
 * all other stores.  Keys are strings and values are Objects.  We use tetrapod serialization
 * so Objects have to be: Integer, Long, String, Boolean, byte[], or Structure subclasses.
 * <p>
 * Note that in general byte[] and Structures are mutable, however they have to be treated as
 * immutable to work with this class.  If you change an object after adding it to a KeyValueStore
 * some, all, or none of your change may be persisted to disk. 
 */
public class KeyValueStore {
   
   public static volatile String DATA_DIRECTORY = "."; 

   private final Map<String,Object> map = new HashMap<>();
   private final String name;
   private final String datadir;
   private final KVEntry entry = new KVEntry(); 
   
   private FileDataSource out = null;
   private int fileNumber = 0;

   public KeyValueStore(String name) throws IOException {
      this(name, DATA_DIRECTORY);
   }
   
   public KeyValueStore(String name, String datadir) throws IOException {
      this.name = name;
      this.datadir = datadir;
      load();
   }
   
   public synchronized int size() { 
      return map.size(); 
   }
   
   public synchronized Object get(String key) {
      return map.get(key);
   }
   
   public synchronized void close() throws IOException {
      if (out != null)
         out.close();
   }
   
   public synchronized void put(String key, Object value) throws IOException {
      persistPut(key, value);
      map.put(key, value);
   }

   public synchronized Object putIf(String key, Object value, Object oldValue) throws IOException {
      Object old = get(key);
      if (old == oldValue) {
         persistPut(key, value);
         map.put(key, value);
         return value;
      }
      return old;
   }
   
   public synchronized void remove(String key) throws IOException {
      persistRemove(key);
      map.remove(key);
   }

   public synchronized void clear() throws IOException {
      persistClear();
      map.clear();
   }
   
   public synchronized void register(Structure s) {
      StructureFactory.add(s);
   }

   private void load() throws IOException {
      File dir = new File(datadir);
      String[] files = dir.list(new FilenameFilter() {
         @Override
         public boolean accept(File dir, String n) {
            return n.startsWith(name);
         }
      });
      int smallest = Integer.MAX_VALUE;
      int biggest = 0;
      for (String f : files) {
         int n = Integer.parseInt(f.substring(name.length() + 1));
         biggest = Math.max(biggest, n);
         smallest = Math.min(smallest, n);
      }
      for (int i = smallest; i <= biggest; i++) 
         load(i);
      fileNumber++;
   }
   
   private void load(int i) throws IOException {
      Path path = FileSystems.getDefault().getPath(datadir, name + "." + i);
      if (Files.exists(path)) {
         fileNumber = i;
         FileDataSource f = FileDataSource.forReading(path);
         while (!f.atEnd()) {
            entry.read(f);
            switch (entry.command) {
               case KVEntry.COMMAND_CLEAR:
                  map.clear();
                  break;
               case KVEntry.COMMAND_REMOVE:
                  map.remove(entry.key);
                  break;
               case KVEntry.COMMAND_SET:
                  map.put(entry.key, entry.value);
                  break;
            }
         }
         f.close();
      }
   }
   
   private void persistPut(String key, Object value) throws IOException {
      openOut();
      entry.save(key, value);
      entry.write(out);
      out.flush();
   }

   private void persistRemove(String key) throws IOException {
      openOut();
      entry.remove(key);
      entry.write(out);
      out.flush();
   }
   
   private void persistClear() throws IOException {
      openOut();
      entry.clear();
      entry.write(out);
      out.flush();
   }
   
   private void openOut() throws IOException {
      if (out == null) {
         Path path = FileSystems.getDefault().getPath(datadir, name + "." + fileNumber);
         out = FileDataSource.forAppending(path);
      }
   }

   public synchronized int incrementInteger(String key) throws IOException {
      Integer x = (Integer)get(key);
      int next = x == null ? 1 : x.intValue() + 1;
      put(key, next);
      return next;
   }
   

}
