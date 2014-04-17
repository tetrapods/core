define(["knockout", "jquery", "bootbox", "app"], function(ko, $, bootbox, app) {
   // static variables
   
   var chartOptions = {
         grid : {
            labelMargin : 0,
            axisMargin : 0,
            margin : {
               top : 0,
               right : 0,
               bottom : 0,
               left : 0
            },
            borderWidth : 0,
            color : "rgba(212, 212, 212, 0.25)"
         },
         xaxis : {
            show : false
         },
         yaxis : {
            show : false,
            min : 0
         },
         legend : {
            show : false,
         }
      };
   
   var Core = app.server.consts["Tetrapod.Core"];

   return Service; // not using new means this returns a constructor function (ie class)
   
   // Service Model
   function Service(entity) {
      var self = this;
      self.entityId = entity.entityId;
      self.name = entity.name;
      self.host = entity.host;
      self.status = ko.observable(entity.status);
      self.commands = ko.observableArray();
      self.mps = ko.observable();
      self.rps = ko.observable();
      self.latency = ko.observable();
      self.counter = ko.observable();

      self.iconURL = ko.observable("media/gear.gif");

      app.server.send("ServiceStatsSubscribe", {}, self.entityId).handle(
            app.server.logResponse)

      app.server
            .send("ServiceDetails", {}, self.entityId)
            .handle(
                  function(result) {
                     if (!result.isError()) {
                        self.iconURL(result.iconURL);
                        self.metadata = result.metadata;
                        if (result.commands) {
                           for (var i = 0; i < result.commands.length; i++) {
                              self.commands
                                    .push(result.commands[i]);
                           }
                        }
                     }
                  });

      self.execute = function(command) {
         app.server.sendRequest(command.contractId, command.structId, {},
               self.entityId).handle(app.server.logResponse)
      }

      self.entityString = ko.computed(function() {
         return self.entityId + " (0x" + self.entityId.toString(16)
               + ")";
      });

      self.isPaused = function() {
         return (self.status() & Core.STATUS_PAUSED) != 0;
      }

      self.isGone = function() {
         return (self.status() & Core.STATUS_GONE) != 0;
      }

      self.isStarting = function() {
         return (self.status() & Core.STATUS_STARTING) != 0;
      }

      self.isFailed = function() {
         return (self.status() & Core.STATUS_FAILED) != 0;
      }

      self.isStopping = function() {
         return (self.status() & Core.STATUS_STOPPING) != 0;
      }

      self.statusName = ko.computed(function() {
         if (self.isGone())
            return "GONE";
         if (self.isStopping())
            return "STOPPING";
         if (self.isFailed())
            return "FAILED";
         if (self.isPaused())
            return "PAUSED";
         if (self.isStarting())
            return "STARTING";
         return "RUNNING";
      });

      self.row_style = ko.computed(function() {
         if (self.isStopping())
            return "stopping";
         if (self.isPaused())
            return "paused";
         if (self.isStarting())
            return "starting";
         return "";
      });

      self.status_style = ko.computed(function() {
         return self.statusName().toLowerCase() + " centered";
      });

      self.pause = function() {
         app.server.send("Pause", {}, self.entityId);
      }

      self.unpause = function() {
         app.server.send("Unpause", {}, self.entityId);
      }
      self.restart = function() {
         bootbox.confirm("Are you sure you want to restart service: "
               + self.name + "[" + self.entitId + "]?", function(
               result) {
            if (result) {
               app.server.send("Restart", {}, self.entityId);
            }
         });
      }
      self.shutdown = function() {
         bootbox.confirm("Are you sure you want to shutdown service: "
               + self.name + "[" + self.entitId + "]?", function(
               result) {
            if (result) {
               app.server.send("Shutdown", {}, self.entityId);
            }
         });
      }

      self.deleteService = function() {
         app.server.send("Unregister", {
            entityId : self.entityId
         }, Core.DIRECT);
      }

      self.canPause = function() {
         return !self.isGone() && !self.isPaused();
      }

      self.canUnpause = function() {
         return !self.isGone() && self.isPaused();
      }

      self.statsUpdate = function(msg) {
         self.latency(msg.latency);
         self.rps(msg.rps);
         self.mps(msg.mps);
         self.counter(msg.counter);
      }

      //////////////////////////////////////// stats graphs ////////////////////////////////////////
      self.plots = [];
      self.getPlot = function(tag) {
         if (self.plots[tag]) {
            return self.plots[tag];
         }
         var container = $("#service-chart-" + tag + "-" + self.entityId);
         if (!container)
            return;

         return $.plot(container, [], chartOptions);
      }

      self.updatePlot = function(tag, series, timeRange, value) {
         series.data.push([ Date.now(), value ]);
         while (series.data[0][0] < Date.now() - timeRange) {
            series.data.shift();
         }
         if (series.maxY != 0) {
            chartOptions.yaxis.max = series.maxY;
         } else {
            chartOptions.yaxis.max = null;
         }
         plot = self.getPlot(tag);
         if (plot) {
            plot.setData([ series ]);
            plot.setupGrid();
            plot.draw();
         }
      }

      self.latencySeries = {
         data : [],
         maxY : 500,
         lines : {
            lineWidth : 1,
            fill : true
         },
         shadowSize : 1
      };
      self.rpsSeries = {
         data : [],
         lines : {
            lineWidth : 1,
            fill : true
         },
         shadowSize : 1
      };
      self.mpsSeries = {
         data : [],
         lines : {
            lineWidth : 1,
            fill : true
         },
         shadowSize : 1
      };
      self.counterSeries = {
         data : [],
         lines : {
            lineWidth : 1,
            fill : true
         },
         shadowSize : 1
      };
      // updates all charts for this service
      self.chart = function() {
         self.updatePlot("latency", self.latencySeries, 60000, self
               .latency());
         self.updatePlot("rps", self.rpsSeries, 60000, self.rps());
         self.updatePlot("mps", self.mpsSeries, 60000, self.mps());
         self.updatePlot("counter", self.counterSeries, 60000, self
               .counter());
      }

   }
});