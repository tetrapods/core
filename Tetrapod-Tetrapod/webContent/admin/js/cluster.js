define(["knockout", "jquery", "app", "host", "service", "raftnode"], function(ko, $, app, Host, Service, RaftNode) {
   var Core = app.server.consts["Core.Core"];

   return new ClusterModel();

   function ClusterModel() {
      var self = this;

      self.hosts = ko.observableArray([]);
      self.services = ko.observableArray([]);
      self.rafts = ko.observableArray([]);

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
         self.hosts.push(host);
         return host;
      }

      function updateRaftNode(service) {
         for (var i = 0; i < self.rafts().length; i++) {
            var raft = self.rafts()[i];
            if (raft.entityId == service.entityId) {
               return raft.update();
            }
         }
         self.rafts.push(new RaftNode(service));
      }

   }

});