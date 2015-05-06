define(["knockout", "jquery", "app", "host", "service", "raftnode"], function(ko, $, app, Host, Service, RaftNode) {
   var Core = app.server.consts["Core.Core"];

   return new ClusterModel();

   function ClusterModel() {
      var self = this;

      self.hosts = ko.observableArray([]);
      self.services = ko.observableArray([]);
      self.rafts = ko.observableArray([]);

      self.leaderEntityId = ko.pureComputed(leaderEntityId);
      self.tolerance = ko.pureComputed(tolerance);
      self.ensurePeer = ensurePeer;
      self.isNodeInCluster = isNodeInCluster;

      var raftTab = $('#raft-tab');

      // Timer to update charts
      setInterval(function updateCharts() {
         $.each(self.services(), function(i, s) {
            s.update();
            if (raftTab.is(':visible')) {
               if (s.isRaftNode()) {
                  updateRaftNode(s);
               }
            }
         });
      }, 1000);

      self.findService = function(entityId) {
         for (var i = 0; i < self.services().length; i++) {
            if (self.services()[i].entityId == entityId) {
               return self.services()[i];
            }
         }
         return null;
      }

      self.onClearAllErrors = function() {
         for (var i = 0; i < self.services().length; i++) {
            self.services()[i].clearErrors();
         }
      };

      app.server.addMessageHandler("ServiceAdded", function(msg) {
         var s = self.findService(msg.entity.entityId);
         if (s) {
            self.services.remove(s);
         }
         s = new Service(msg.entity);
         self.services.push(s);
         self.services.sort(compareServices);
         self.getHost(s.host).addService(s);
      });

      app.server.addMessageHandler("ServiceUpdated", function(msg) {
         var s = self.findService(msg.entityId);
         if (s) {
            s.status(msg.status);
         }
      });

      app.server.addMessageHandler("ServiceRemoved", function(msg) {
         var s = self.findService(msg.entityId);
         if (s) {
            self.services.remove(s);
            self.getHost(s.host).removeService(s);
         }
      });

      app.server.addMessageHandler("ServiceStats", function(msg) {
         var s = self.findService(msg.entityId);
         if (s) {
            s.statsUpdate(msg);
         }
      });

      function compareServices(a, b) {
         return (a.entityId - b.entityId);
      }

      self.getHost = function(hostname) {
         for (var i = 0; i < self.hosts().length; i++) {
            var host = self.hosts()[i];
            if (host.hostname == hostname) {
               return host;
            }
         }
         var host = new Host(hostname);
         var array = self.hosts();
         array.push(host);
         array.sort(function(a,b) {
            return a.hostname == b.hostname ? 0 : (a.hostname < b.hostname ? -1 : 1);
         });
         self.hosts(array);
         return host;
      }

      // we received up to date stats for a raft node -- update or add a RaftNode object to the clusster details
      function updateRaftNode(service) {
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.entityId == service.entityId) {
               raft.host = service.host;
               return raft.update();
            }
         }
         self.rafts.push(new RaftNode(service.entityId, service.host, self));
      }

      // if a node is reporting existence of a peer, make sure our list contains it
      function ensurePeer(peerEntityId) {
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.entityId == peerEntityId) {
               return;
            }
         }
         self.rafts.push(new RaftNode(peerEntityId, null, self));
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
            if (raft.isHealthy()) {
               nodes++;
            }
         }
         return Math.floor(nodes / 2);
      }

   }

});