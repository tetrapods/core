package io.tetrapod.core;

import io.tetrapod.core.utils.Properties;

import java.io.*;

public class ServerOptions {

   public static void launch(String[] args) throws IOException {
      if (args.length < 1) {
         usage();
         return;
      }
      Properties props = new Properties();
      props.load(new File(args[0]));
      int i = 1;
      boolean start = false;
      String connectHost = null;
      int connectPort = 0;
      String nonce = null;
      String name = null;
      while (i < args.length) {
         String command = args[i];
         switch (command) {
            case "start":
               start = true;
               i++;
               break;
            case "connect":
               connectHost = args[i+1];
               int ix = connectHost.indexOf(':');
               if (ix >= 0) {
                  connectPort = Integer.parseInt(connectHost.substring(ix+1));
                  connectHost = connectHost.substring(0, ix);
                  i += 2;
               }
               break;
            case "nonce":
               nonce = args[i+1];
               i += 2;
               break;
            case "name":
               name = args[i+1];
               i += 2;
               break;
            default:
               usage();
               return;
         }
      }
      Application app = new Application();
      app.init(props, start, connectHost, connectPort, nonce, name);
   }
   
   private static void usage() {
      System.err.println("usage: <java args> propertiesFile [commands]\n");
      System.err.println("known commands, all are optional:\n");
      System.err.println("   start                     (if not present service will start paused)\n");
      System.err.println("   connect hostname[:port]   (of a tetrapod service)\n");
      System.err.println("   nonce nonceValue          (used for reclaims)\n");
      System.err.println("   name nameString           (overrides name in properties file)\n");
   }
}
