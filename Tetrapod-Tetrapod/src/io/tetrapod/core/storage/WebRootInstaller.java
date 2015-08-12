package io.tetrapod.core.storage;

import io.tetrapod.core.utils.Util;
import io.tetrapod.core.web.*;
import io.tetrapod.protocol.core.WebRootDef;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

public class WebRootInstaller {
   private static final Logger logger = LoggerFactory.getLogger(WebRootInstaller.class);

   private final Executor                webRootSequentialExecutor = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS,
                                                                         new LinkedBlockingQueue<Runnable>());
   private final TetrapodStateMachine    stateMachine;
   private final Map<String, WebRootDef> pending                   = new HashMap<>();

   public WebRootInstaller(TetrapodStateMachine sm) {
      stateMachine = sm;
   }

   public void install(final WebRootDef def) {
      synchronized (pending) {
         pending.put(def.name, def);
      }
      webRootSequentialExecutor.execute(new Runnable() {
         @Override
         public void run() {
            doInstall(def);
         }
      });
   }

   public void uninstall(final String name) {
      synchronized (pending) {
         pending.remove(name);
      }
      webRootSequentialExecutor.execute(new Runnable() {
         @Override
         public void run() {
            doUninstall(name);
         }
      });
      
   }

   private void doInstall(WebRootDef def) {
      try {
         synchronized (pending) {
            if (pending.get(def.name) != def) {
               // a later add or remove happened on my name, skip this install
               return;
            }
            pending.remove(def.name);
         }
         if (!Util.isEmpty(def.file) && !Util.isEmpty(def.path)) {
            WebRoot wr = null;
            logger.debug("Installing WebRoot {} from {} on path {}", def.name, def.file, def.path);
            if (def.file.startsWith("http")) {
               wr = new WebRootLocalFilesystem(def.path, new URL(def.file));
            } else {
               wr = new WebRootLocalFilesystem(def.path, new File(def.file));
            }
            stateMachine.webRootDirs.put(def.name, wr);
         }
      } catch (IOException e) {
         logger.error(e.getMessage(), e);
      }
   }
   
   private void doUninstall(String name) {
      logger.debug("Uninstalling WebRoot {} ", name);
      stateMachine.webRootDirs.remove(name);
   }

}
