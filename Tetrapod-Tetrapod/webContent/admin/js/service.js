define([ "knockout", "jquery", "bootbox", "app", "build" ], function(ko, $, bootbox, app, builder) {
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

   var Core = app.server.consts["Core.Core"];

   return Service; // not using new means this returns a constructor function (ie class)

   // Service Model
   function Service(entity) {
      var self = this;
      self.entityId = entity.entityId;
      self.name = entity.name;
      self.host = entity.host;
      self.status = ko.observable(entity.status);
      self.commands = ko.observableArray();
      self.build = entity.build;
      self.mps = ko.observable();
      self.rps = ko.observable();
      self.latency = ko.observable();
      self.counter = ko.observable();
      self.disk = ko.observable();
      self.memory = ko.observable();
      self.load = ko.observable();
      self.threads = ko.observable();

      self.iconURL = ko.observable("media/gear.gif");

      app.server.send("ServiceStatsSubscribe", {}, self.entityId).handle(app.server.logResponse)

      app.server.send("ServiceDetails", {}, self.entityId).handle(function(result) {
         if (!result.isError()) {
            self.iconURL(result.iconURL);
            self.metadata = result.metadata;
            if (result.commands) {
               for (var i = 0; i < result.commands.length; i++) {
                  self.commands.push(result.commands[i]);
               }
            }
         }
      });

      self.execute = function(command) {
         if (command.hasArgument) {
            bootbox.prompt("Enter argument value:", function(result) {                
               if (result !== null) {                                             
                  app.server.sendRequest(command.contractId, command.structId, { data: result }, self.entityId).handle(app.server.logResponse);
               }
             }); 
         } else {
            app.server.sendRequest(command.contractId, command.structId, {}, self.entityId).handle(app.server.logResponse);
         }
      }

      self.entityString = ko.computed(function() {
         return self.entityId + " (0x" + self.entityId.toString(16) + ")";
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
         bootbox.confirm("Are you sure you want to restart service: " + self.name + "[" + self.entityId + "]?",
               function(result) {
                  if (result) {
                     app.server.send("Restart", {}, self.entityId);
                  }
               });
      }
      self.shutdown = function() {
         bootbox.confirm("Are you sure you want to shutdown service: " + self.name + "[" + self.entityId + "]?",
               function(result) {
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
         self.load(msg.load);
         self.memory(msg.memory);
         self.disk(msg.disk);
         self.threads(msg.threads);
      }
      self.memoryWidth = ko.computed(function() {
         return self.memory() + '%';
      }, self);

      // ////////////////////////////////////// stats graphs ////////////////////////////////////////
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
         self.updatePlot("latency", self.latencySeries, 60000, self.latency());
         self.updatePlot("rps", self.rpsSeries, 60000, self.rps());
         self.updatePlot("mps", self.mpsSeries, 60000, self.mps());
         self.updatePlot("counter", self.counterSeries, 60000, self.counter());
      }

      self.popupBuild = function() {
         builder.load(self.entityId);
      }

      var LogConsts = app.server.consts["Core.ServiceLogEntry"];

      var levels = [ LogConsts.LEVEL_ALL, LogConsts.LEVEL_TRACE, LogConsts.LEVEL_DEBUG, LogConsts.LEVEL_INFO,
            LogConsts.LEVEL_WARN, LogConsts.LEVEL_ERROR, LogConsts.LEVEL_OFF ];

      self.getLogLevelStyle = function(level) {
         switch (level) {
         case LogConsts.LEVEL_ALL:
            return 'all';
         case LogConsts.LEVEL_TRACE:
            return 'trace';
         case LogConsts.LEVEL_DEBUG:
            return 'debug';
         case LogConsts.LEVEL_INFO:
            return 'info';
         case LogConsts.LEVEL_WARN:
            return 'warn';
         case LogConsts.LEVEL_ERROR:
            return 'error';
         case LogConsts.LEVEL_OFF:
            return 'off';
         }
         return '';
      }

      self.logLevels = ko.observableArray(levels);

      var months = [ "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" ];
      function logtime(d) {
         return d.getFullYear() + "-" + months[d.getMonth()] + "-" + d.getDate() + " " + d.getHours() + ":"
               + d.getMinutes() + ":" + d.getSeconds() + ":" + d.getMilliseconds();
      }
      self.logLevel = ko.observable(LogConsts.LEVEL_INFO);
      self.logs = ko.observableArray([]);
      self.autoScrollLogs = ko.observable(true);
      var logRefreshPending = false;

      self.refreshLogs = function() {
         if (!logRefreshPending) {
            logRefreshPending = true;

            app.server.send("ServiceLogs", {
               logId : self.lastLogId,
               level : self.logLevel(),
               maxItems : 100
            }, self.entityId).handle(function(res) {
               logRefreshPending = false;
               if (!res.isError()) {
                  if (self.expanded()) {
                     self.lastLogId = res.lastLogId;
                     if (res.items.length > 0) {
                        $.each(res.items, function(i, item) {
                           item.levelStyle = self.getLogLevelStyle(item.level);
                           item.timestamp = logtime(new Date(item.timestamp))
                           self.logs.push(item);
                        });
                        // remove older items from list
                        while (self.logs.length > 1000) {
                           self.logs.shift();
                        }
                        if (self.autoScrollLogs()) {
                           var scroll = document.getElementById("service-logs-" + self.entityId);
                           scroll.scrollTop = scroll.scrollHeight;
                        }
                     }
                  }
               }
            })
         }
      };

      self.expanded = new ko.observable(false);
      self.showLogs = function() {
         self.expanded(!self.expanded());
         self.logs.removeAll();
         self.lastLogId = 0;
         if (self.expanded()) {
            self.refreshLogs();
         }
      }

      // called by our timer in cluster.js for periodic updates
      self.update = function() {
         self.chart();
         if (self.expanded()) {
            self.refreshLogs();
         }
      };

   }
});