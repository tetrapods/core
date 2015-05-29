define(["knockout", "jquery", "app", "alert", "host", "service", "raftnode", "property", "webroot"], function(ko, $, app, Alert, Host, Service, RaftNode, Property, WebRoot) {
   var Core = app.server.consts["Core.Core"];

   return new ClusterModel();

   function ClusterModel() {
      var self = this;

      self.hosts = ko.observableArray([]);
      self.services = ko.observableArray([]);
      self.rafts = ko.observableArray([]);
      self.props = ko.observableArray([]);
      self.webroots = ko.observableArray([]);

      self.leaderEntityId = ko.pureComputed(leaderEntityId);
      self.tolerance = ko.pureComputed(tolerance);
      self.maxTerm = 0;
      self.ensurePeer = ensurePeer;
      self.isNodeInCluster = isNodeInCluster;
      self.addClusterProperty = addClusterProperty;
      self.addWebRoot = addWebRoot;

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
         var arr = self.services();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].entityId == entityId) {
               return arr[i];
            }
         }
         return null;
      }

      self.findProperty = function(key) {
         var arr = self.props();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].key == key) {
               return arr[i];
            }
         }
         return null;
      }

      self.findWebRoot = function(name) {
         var arr = self.webroots();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].name == name) {
               return arr[i];
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

      app.server.addMessageHandler("ClusterPropertyAdded", function(msg) {
         var p = self.findProperty(msg.property.key);
         if (p) {
            self.props.remove(p);
         }
         p = new Property(msg.property);
         self.props.push(p);
         self.props.sort(compareProperties);
      });

      app.server.addMessageHandler("ClusterPropertyRemoved", function(msg) {
         var p = self.findProperty(msg.key);
         if (p) {
            self.props.remove(p);
         }
      });

      app.server.addMessageHandler("WebRootAdded", function(msg) {
         var wr = self.findWebRoot(msg.def.name);
         if (wr) {
            self.webroots.remove(wr);
         }
         wr = new WebRoot(msg.def);
         self.webroots.push(wr);
         //self.webroots.sort(compareWebRoots);
      });

      app.server.addMessageHandler("WebRootRemoved", function(msg) {
         var wr = self.findWebRoot(msg.name);
         if (wr) {
            self.webroots.remove(wr);
         }
      });

      function compareProperties(a, b) {
         return a.key.localeCompare(b.key);
      }

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
         array.sort(function(a, b) {
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

      function addWebRoot() {
         Alert.prompt("Enter a new web root name", function(name) {
            if (name && name.trim().length > 0) {
               app.server.sendDirect("SetWebRoot", {
                  adminToken: app.authtoken,
                  def: {
                     name: name,
                     path: '',
                     location: '',
                  }
               }, app.server.logResponse);
            }
         });
      }

      function addClusterProperty(secret) {
         Alert.prompt("Enter a new key name", function(key) {
            if (key && key.trim().length > 0) {
               app.server.sendDirect("SetClusterProperty", {
                  adminToken: app.authtoken,
                  property: {
                     key: key,
                     val: '',
                     secret: secret
                  }
               }, app.server.logResponse);
            }
         });
      }

   }

});