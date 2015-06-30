package  io.tetrapod.protocol.raft;

// This is a code generated file.  All edits will be lost the next time code gen is run.

import io.*;
import java.util.*;
import io.tetrapod.core.*;
import io.tetrapod.core.rpc.Structure;
import io.tetrapod.protocol.core.WebRoute;

@SuppressWarnings("unused")
public class RaftContract extends Contract {
   public static final int VERSION = 1;
   public static final String NAME = "Raft";
   public static final int CONTRACT_ID = 16;
   
   public static interface API extends APIHandler
      , AppendEntriesRequest.Handler
      , InstallSnapshotRequest.Handler
      , IssueCommandRequest.Handler
      , VoteRequest.Handler
      {}
   
   public Structure[] getRequests() {
      return new Structure[] {
         new VoteRequest(),
         new AppendEntriesRequest(),
         new InstallSnapshotRequest(),
         new IssueCommandRequest(),
      };
   }
   
   public Structure[] getResponses() {
      return new Structure[] {
         new VoteResponse(),
         new AppendEntriesResponse(),
         new InstallSnapshotResponse(),
         new IssueCommandResponse(),
      };
   }
   
   public Structure[] getMessages() {
      return new Structure[] {
         
      };
   }
   
   public Structure[] getStructs() {
      return new Structure[] {
         new LogEntry(),
      };
   }
   
   public String getName() {
      return RaftContract.NAME;
   } 
   
   public int getContractId() {
      return RaftContract.CONTRACT_ID;
   }
   
   public int getContractVersion() {
      return RaftContract.VERSION;
   }
   
   public WebRoute[] getWebRoutes() {
      return new WebRoute[] {
         
      };
   }

}
