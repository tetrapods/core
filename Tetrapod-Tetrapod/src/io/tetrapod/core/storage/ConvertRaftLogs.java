package io.tetrapod.core.storage;

import io.tetrapod.core.utils.*;
import io.tetrapod.raft.*;

import java.io.*;

/**
 * Developer util to convert raft logs into a human readable form
 */
public class ConvertRaftLogs {

   public static void main(String[] args) throws Exception {
      File logDir = new File(args.length > 0 ? args[0] : "logs/raft");
      File tmpDir = new File(logDir, Rand.nextBase36String(12));
      Config cfg = new Config().setLogDir(tmpDir).setClusterName("Tetrapod");
      RaftEngine<TetrapodStateMachine> raftEngine = null;
      raftEngine = new RaftEngine<TetrapodStateMachine>(cfg, new TetrapodStateMachine.Factory(), null);
      
      for (File f : logDir.listFiles()) {
         if (f.isFile() && f.getName().endsWith("log")) {
            convertFile(f, raftEngine.getLog());
         }
      }
      tmpDir.delete();
      System.out.println("DONE");
      System.exit(1);
   }
   
   private static void convertFile(File inFile, Log<TetrapodStateMachine> log) throws Exception {
      System.out.println(inFile.getName());
      File outFile = new File(inFile.getParent(), "converted/" + inFile.getName() + ".txt");
      outFile.getParentFile().mkdirs();
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
         for (Entry<TetrapodStateMachine> e : log.loadLogFile(inFile)) {
            bw.write(String.format("%d:%d %s\n", e.getTerm(), e.getIndex(), Util.toString(e.getCommand())));
         }
         bw.close();
      }
   }
}
