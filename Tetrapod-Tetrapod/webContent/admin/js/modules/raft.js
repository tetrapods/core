define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");

   return Raft; // not using new means this returns a constructor function (ie class)

   function Raft(app) {
      var self = this;

      var Core = app.server.consts["Core.Core"];

      self.rafts = ko.observableArray([]);

      self.updateRaftNode = updateRaftNode;
      self.clear = clear;
      self.leaderEntityId = ko.pureComputed(leaderEntityId);
      self.tolerance = ko.pureComputed(tolerance);
      self.maxTerm = 0;
      self.ensurePeer = ensurePeer;
      self.isNodeInCluster = isNodeInCluster;

      function clear() {
         self.rafts.removeAll();
      }

      var raftTab = $('#raft-tab');
      setInterval(function() {
         if (raftTab.is(':visible')) {
            $.each(self.rafts(), function(i, raft) {
               raft.update();
            });
         }
      }, 1000);

      app.server.addMessageHandler("ServiceAdded", function(msg) {
         if (msg.entity.type == Core.TYPE_TETRAPOD) {
            self.updateRaftNode(msg.entity.entityId, msg.entity.host);
         }
      });

      // we received up to date stats for a raft node -- update or add a RaftNode object to the clusster details
      function updateRaftNode(entityId, host) {
         var raft = findRaftByEntityId(entityId);
         if (raft) {
            raft.host = host;
            return raft.update();
         }
         self.rafts.push(new RaftNode(entityId, host, self));
      }

      // if a node is reporting existence of a peer, make sure our list contains it
      function ensurePeer(peerEntityId) {
         var raft = findRaftByEntityId(peerEntityId);
         if (!raft) {
            self.rafts.push(new RaftNode(peerEntityId, null, self));
         }
      }

      function findRaftByEntityId(entityId) {
         var arr = self.rafts();
         for (var i = 0; i < arr.length; i++) {
            var raft = arr[i];
            if (raft.entityId == entityId) {
               return raft;
            }
         }
         return null;
      }

      // looks at all healthy nodes and determine the consensus for leaderId
      function leaderEntityId() {
         var leaders = {};
         var votes = 0;
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.isHealthy()) {
               var v = leaders[raft.leaderEntityId()];
               if (!v)
                  v = 0;
               leaders[raft.leaderEntityId()] = v + 1;
               votes++;
               if (v + 1 > votes / 2) {
                  return raft.leaderEntityId();
               }
            }
         }
         return 0; // no leader found?
      }

      // returns the consensus that a given node is in the cluster
      function isNodeInCluster(entityId) {
         var votes = 0;
         var nodes = 0;
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.isHealthy()) {
               nodes++;
               if (raft.hasPeer(entityId)) {
                  votes++;
               }
            }
         }
         return votes + 1 > nodes / 2;
      }

      // returns the number of node failures this cluster should be able to safely tolerate.
      function tolerance() {
         var nodes = 0;
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.curTerm() > self.maxTerm) {
               self.maxTerm = raft.curTerm();
            }
         }
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.isHealthy()) {
               nodes++;
            }
         }
         return Math.floor((nodes - 1) / 2);
      }

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
            if (self.curTerm() < cluster.maxTerm)
               return false;
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
   }
});