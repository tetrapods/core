/**
 * A wrapper object for details about a raft node in the cluster
 */
define(["knockout", "jquery", "bootbox", "app", "build", "chart"], function(ko, $, bootbox, app, builder, Chart) {

   return RaftNode; // not using new means this returns a constructor function (ie class)

   // RaftNode Model
   function RaftNode(entityId, host, cluster) {
      var self = this;

      self.entityId = entityId;
      self.host = host;

      self.role = ko.observable();
      self.curTerm = ko.observable(0);
      self.lastTerm = ko.observable(0);
      self.lastIndex = ko.observable(0);
      self.commitIndex = ko.observable(0);
      self.numPeers = ko.observable(0);
      self.lastContact = ko.observable(0);
      self.leaderId = ko.observable(0);
      self.peers = ko.observableArray([]);
      self.leaderEntityId = ko.pureComputed(function() {
         return self.leaderId() << 20; /* Registry.PARENT_ID_SHIFT */
      });
      self.inCluster = ko.pureComputed(function() {
         return cluster.isNodeInCluster(self.entityId);
      });

      self.update = update;
      self.leaveCluster = leaveCluster;
      self.isHealthy = isHealthy;
      self.hasPeer = hasPeer;

      self.update();

      function update() {
         app.server.sendTo("RaftStats", {}, self.entityId, function(info) {
            if (!info.isError()) {
               self.role(info.role);
               self.curTerm(info.curTerm);
               self.lastTerm(info.lastTerm);
               self.lastIndex(info.lastIndex);
               self.commitIndex(info.commitIndex);
               self.numPeers(info.peers.length);
               self.peers(info.peers);
               self.lastContact(new Date());
               self.leaderId(info.leaderId);

               for (var i = 0; i < info.peers.length; i++) {
                  cluster.ensurePeer(info.peers[i]);
               }

            } else {
               self.role(-1);
            }
         });
      }

      self.roleName = ko.pureComputed(function() {
         switch (self.role()) {
         case 0:
            return 'Joining';
         case 1:
            return 'Observer';
         case 2:
            return 'Follower';
         case 3:
            return 'Candidate';
         case 4:
            return 'Leader';
         case 5:
            return 'Failed';
         case 6:
            return 'Leaving';
         }
         return 'Unknown';
      });

      self.roleIcon = ko.pureComputed(function() {
         switch (self.role()) {
         case 0:
            return 'fa-ellipsis-h';
         case 1:
            return 'fa-dot-circle-o';
         case 2:
            return 'fa-circle-o';
         case 3:
            return 'fa-star-o';
         case 4:
            return 'fa-star';
         case 5:
            return 'fa-bomb';
         case 6:
            return 'fa-chain-broken';
         }
         return 'fa-question';
      });

      function leaveCluster() {
         app.server.sendTo("ClusterLeave", {
            entityId: self.entityId
         }, cluster.leaderEntityId(), function(info) {
            if (info.isError()) {
               console.error("Cluster Leave Failed");
            }
         });
      }

      // return true if this node is part of the cluster. 
      // FIXME: this should probably have better logic
      function isHealthy() {
         return self.role() == 2 || self.role() == 3 || self.role() == 4;
      }

      function hasPeer(entityId) {
         for (var i = 0; i < self.peers().length; i++) {
            if (self.peers()[i] == entityId) {
               return true;
            }
         }
         return false;
      }

   }
});