/**
 * Host.js is a wrapper object for details about a host machine in the cluster. A host may have one or more services
 * running on it, and we can collect and display host information like CPU usage, free disk, etc...
 */
define(["knockout", "jquery", "bootbox", "app", "build", "chart"], function(ko, $, bootbox, app, builder, Chart) {

   return Host; // not using new means this returns a constructor function (ie class)

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
      }

      self.addService = function(s) {
         var old = self.findService(s.entityId);
         if (old) {
            self.services.remove(old);
         }
         self.services.push(s);
         self.services.sort(compareServices);
      }

      self.removeService = function(s) {
         self.services.remove(s);
      }

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
});