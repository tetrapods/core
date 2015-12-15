define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");
   var app = require("app");
   var Service = require("service");
   var Chart = require("chart");
   var Builder = require("./builder");

   return Hosts; // not using new means this returns a constructor function (ie class)

   function Hosts(app) {

      var self = this;
      var Core = app.server.consts['Core'];

      self.hosts = ko.observableArray([]);
      self.services = ko.observableArray([]);

      self.getHost = getHost;
      self.findService = findService;
      self.clear = clear;
      self.onClearAllErrors = onClearAllErrors;
      self.deployBuilds = deployBuilds;

      // Timer to update charts
      setInterval(function updateCharts() {
         $.each(self.services(), function(i, s) {
            s.update();
         });
      }, 1000);

      function clear() {
         self.hosts.removeAll();
         self.services.removeAll();
      }

      function findService(entityId) {
         var arr = self.services();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].entityId == entityId) {
               return arr[i];
            }
         }
         return null;
      }

      function onClearAllErrors() {
         for (var i = 0; i < self.services().length; i++) {
            self.services()[i].clearErrors();
         }
      }

      app.server.addMessageHandler("ServiceAdded", function(msg) {
         var s = self.findService(msg.entity.entityId);
         if (s) {
            self.services.remove(s);
         }
         s = new Service(msg.entity);
         self.services.push(s);
         self.services.sort(compareServices);
         self.getHost(msg.entity.host).addService(s);
         s.subscribe(1);
      });

      app.server.addMessageHandler("ServiceUpdated", function(msg) {
         var s = self.findService(msg.entityId);
         if (s) {
            var wasGone = s.isGone();
            s.status(msg.status);
            // resub when service returns
            if (wasGone && !s.isGone()) {
               s.subscribe(1); 
            }
         }
      });

      app.server.addMessageHandler("ServiceRemoved", function(msg) {
         var s = self.findService(msg.entityId);
         if (s) {
            self.services.remove(s);
            self.getHost(s.host).removeService(s);
            s.removed = true;
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

      function getHost(hostname) {
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
      
      function deployBuilds() {
         var tetrapods = [];
         for (var i = 0; i < self.services().length; i++) {
            var s = self.services()[i];
            if (s.name.indexOf("Tetrapod") == 0) {
               tetrapods.push(s);
            }
         }
         Builder.load(tetrapods);
      }

      // Host Model
      function Host(hostname) {
         var self = this;

         self.services = ko.observableArray([]); // services running on this host      

         self.hostname = hostname;
         self.cores = ko.observable(0);
         self.disk = ko.observable(0);
         self.memory = ko.observable(0);
         self.load = ko.observable(0);

         self.loadChart = new Chart('host-chart-load-' + hostname, self.load);
         self.diskChart = new Chart('host-chart-disk-' + hostname, self.disk);

         self.loadChart.series.maxY = 2;
         self.diskChart.series.maxY = 1024 * 1024 * 1024;

         // start collecting details
         updateHostDetails();
         updateHostStats();

         // polls a service from this host for host details
         function updateHostDetails() {
            var s = getAvailableService();
            if (s) {
               app.server.sendTo("HostInfo", {}, s.entityId, function(result) {
                  if (!result.isError()) {
                     self.cores(result.numCores);
                  }
               });
            } else {
               setTimeout(updateHostDetails, 1000);
            }
         }

         // polls a service from this host for host details
         function updateHostStats() {
            var s = getAvailableService();
            if (s) {
               app.server.sendTo("HostStats", {}, s.entityId, function(result) {
                  if (!result.isError()) {
                     // TODO: update model & charts
                     self.load(result.load.toFixed(1));
                     self.disk(result.disk);
                  }
                  setTimeout(updateHostStats, 5000);
                  self.loadChart.updatePlot(60000, self.load());
                  self.diskChart.updatePlot(60000, self.disk());
               });
            } else {
               setTimeout(updateHostStats, 1000);
            }
         }

         var iter = 0;
         // returns any available service running on this host 
         function getAvailableService() {
            var arr = self.services();
            for (var i = 0; i < arr.length; i++) {
               var s = arr[(i + iter++) % arr.length];
               if (!s.isGone()) {
                  return s;
               }
            }
            return null;
         }

         self.findService = function(entityId) {
            for (var i = 0; i < self.services().length; i++) {
               if (self.services()[i].entityId == entityId) {
                  return self.services()[i];
               }
            }
            return null;
         };

         self.addService = function(s) {
            var old = self.findService(s.entityId);
            if (old) {
               self.services.remove(old);
            }
            self.services.push(s);
            self.services.sort(compareServices);
         };

         self.removeService = function(s) {
            self.services.remove(s);
         };

         function compareServices(a, b) {
            return (a.entityId - b.entityId);
         }

         self.diskLabel = ko.pureComputed(function() {
            var d = self.disk() / (1024 * 1024); // mb
            if (d > 10000) {
               return (d / 1024).toFixed(1) + " gb";
            }
            return d.toFixed(1) + " mb";
         });

         self.onClearAllErrors = function() {
            for (var i = 0; i < self.services().length; i++) {
               self.services()[i].clearErrors();
            }
         };
      }
   }
});
