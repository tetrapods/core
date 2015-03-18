define(["knockout", "jquery", "app", "host", "service"], function(ko, $, app, Host, Service) {
   var Core = app.server.consts["Core.Core"];

   return new ClusterModel();

   function ClusterModel() {
      var self = this;

      self.raft = ko.observableArray([]);
      self.hosts = ko.observableArray([]);
      self.services = ko.observableArray([]);

      // Timer to update charts
      setInterval(function updateCharts() {
         $.each(self.services(), function(i, s) {
            s.update();
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

   }

});