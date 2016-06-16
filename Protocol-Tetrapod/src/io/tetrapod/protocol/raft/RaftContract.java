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
   
   private Structure[] requests = null;

   public Structure[] getRequests() {
      if (requests == null) {
         requests = new Structure[] {
            new VoteRequest(),
            new AppendEntriesRequest(),
            new InstallSnapshotRequest(),
            new IssueCommandRequest(),
         };
      }
      return requests;
   }
   
   private Structure[] responses = null;

   public Structure[] getResponses() {
      if (responses == null) {
         responses = new Structure[] {
            new VoteResponse(),
            new AppendEntriesResponse(),
            new InstallSnapshotResponse(),
            new IssueCommandResponse(),
         };
      }
      return responses;
   }
   
   private Structure[] messages = null;

   public Structure[] getMessages() {
      if (messages == null) {
         messages = new Structure[] {
            
         };
      }
      return messages;
   }
   
   private Structure[] structs = null;

   public Structure[] getStructs() {
      if (structs == null) {
         structs = new Structure[] {
            new LogEntry(),
         };
      }
      return structs;
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

   private WebRoute[] webRoutes = null;

   public WebRoute[] getWebRoutes() {
      if (webRoutes == null) {
         webRoutes = new WebRoute[] {
            
         };
      }
      return webRoutes;
   }

   public static final int ERROR_NO_LEADER = 13434878; 
}
