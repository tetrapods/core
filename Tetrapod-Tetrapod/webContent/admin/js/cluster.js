define(["knockout", "jquery", "app", "service"], function(ko, $, app, Service) {
   var Core = app.server.consts["Core.Core"];

   return new ClusterModel();
   
   function ClusterModel() {
      var self = this;
      self.services = ko.observableArray([]);

      // Timer to update charts
      setInterval(function updateCharts() {
         $.each(self.services(), function(i, s) {
            s.chart();
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
         self.services.push(new Service(msg.entity));
         self.services.sort(compareServices);
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
   }

});