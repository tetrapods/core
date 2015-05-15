define(["knockout", "jquery", "bootbox", "app", "build", "chart"], function(ko, $, bootbox, app, builder, Chart) {
   // static variables

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
      self.isSelected = ko.observable(false);

      self.iconURL = ko.observable("media/gear.gif");

      app.server.sendTo("ServiceStatsSubscribe", {}, self.entityId, app.server.logResponse)

      app.server.sendTo("ServiceDetails", {}, self.entityId, function(result) {
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

      // we need to run this after KO is done binding, or jquery can't find the element by id
      setTimeout(function() {
         var e = $('#dropdown-' + self.entityId);
         e.on('show.bs.dropdown', function() {
            self.isSelected(true);
         });
         e.on('hide.bs.dropdown', function() {
            self.isSelected(false);
         });
      }, 1);

      self.execute = function(command) {
         if (command.hasArgument) {
            bootbox.prompt("Enter argument value:", function(result) {
               if (result !== null) {
                  app.server.sendRequest(command.contractId, command.structId, {
                     data: result
                  }, self.entityId, app.server.logResponse);
               }
            });
         } else {
            app.server.sendRequest(command.contractId, command.structId, {}, self.entityId, app.server.logResponse);
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

      self.isPassive = function() {
         return (self.status() & Core.STATUS_PASSIVE) != 0;
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
         if (self.isPassive())
            return "PASSIVE";
         return "RUNNING";
      });

      self.row_style = ko.pureComputed(function() {
         if (self.isSelected())
            return "selected";
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
         app.server.sendTo("Pause", {}, self.entityId);
      }

      self.purge = function() {
         app.server.sendTo("Purge", {}, self.entityId);
      }

      self.unpause = function() {
         app.server.sendTo("Unpause", {}, self.entityId);
      }
      self.restart = function() {
         bootbox.confirm("Are you sure you want to restart service: " + self.name + "[" + self.entityId + "]?", function(result) {
            if (result) {
               app.server.sendTo("Restart", {}, self.entityId);
            }
         });
      }
      self.shutdown = function() {
         bootbox.confirm("Are you sure you want to shutdown service: " + self.name + "[" + self.entityId + "]?", function(result) {
            if (result) {
               app.server.sendTo("Shutdown", {}, self.entityId);
            }
         });
      }

      self.clearErrors = function() {
         app.server.sendTo("ResetServiceErrorLogs", {}, self.entityId);
      }

      self.showErrors = function() {
         app.server.sendTo("ServiceErrorLogs", {}, self.entityId, function(result) {
            if (!result.isError()) {
               var messages = [];
               messages.push('<div class="service-logs" style="height: 100%; min-height: 200px">');
               messages.push('<ul>');

               for (var i = 0; i < result.errors.length; i++) {
                  var error = result.errors[i];
                  var levelStyle = self.getLogLevelStyle(error.level);
                  var timestamp = logtime(new Date(error.timestamp));
                  messages.push('<li class="' + levelStyle + '">');
                  messages.push(timestamp + "&nbsp;");
                  messages.push(error.thread + "&nbsp;");
                  messages.push(error.logger);
                  messages.push('<span class="service-logs-msg">' + error.msg + '</span>');
                  messages.push('</li>');
               }
               messages.push('</ul>');
               messages.push('</div>');

               var text = messages.join("");
               bootbox.dialog({
                  message: text
               }).find("div.modal-dialog").addClass("service-error-logs-dialog");
            }
         });
      }

      self.hasErrors = ko.pureComputed(function() {
         return (self.status() & Core.STATUS_ERRORS) != 0;
      });

      self.hasWarningsOnly = ko.pureComputed(function() {
         return (self.status() & Core.STATUS_WARNINGS) != 0 && (self.status() & Core.STATUS_ERRORS) == 0;
      });

      self.hasErrorsOrWarnings = ko.pureComputed(function() {
         return (self.status() & Core.STATUS_ERRORS) != 0 || (self.status() & Core.STATUS_WARNINGS) != 0;
      });

      self.setCommsLogLevel = function() {
         bootbox.dialog({
            message: 'Log Level: <select id="level">' + '<option value="off">Off</option>' + '<option value="error">Error</option>' + '<option value="warn">Warning</option>' + '<option value="info">Info</option>' + '<option value="debug">Debug</option>' + '<option value="trace">Trace</option>' + '<option value="all">All</option>' + '</select>',
            buttons: {
               success: {
                  label: "OK",
                  className: "btn-success",
                  callback: function() {
                     var level = $('#level').val();
                     app.server.sendTo("SetCommsLogLevel", {
                        level: level
                     }, self.entityId);
                  }
               },
               cancel: {
                  label: "Cancel",
                  className: "btn-cancel"
               }
            }
         });
      }

      self.deleteService = function() {
         app.server.sendDirect("Unregister", {
            entityId: self.entityId
         });
      }

      self.canPurge = ko.pureComputed(function() {
         return self.isPaused();
      }

      self.canPause = ko.pureComputed(function() {
         return !self.isGone() && !self.isPaused();
      });

      self.canUnpause = ko.pureComputed(function() {
         return !self.isGone() && self.isPaused();
      });

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

      self.isRaftNode = ko.pureComputed(function() {
         return entity.type == Core.TYPE_TETRAPOD;
      });

      // ////////////////////////////////////// stats graphs ////////////////////////////////////////

      self.latencyChart = new Chart("service-chart-latency-" + self.entityId);
      self.rpsChart = new Chart("service-chart-rps-" + self.entityId);
      self.mpsChart = new Chart("service-chart-mps-" + self.entityId);
      self.counterChart = new Chart("service-chart-counter-" + self.entityId);

      self.latencyChart.series.maxY = 500;

      // updates all charts for this service
      self.chart = function() {
         self.latencyChart.updatePlot(60000, self.latency());
         self.rpsChart.updatePlot(60000, self.rps());
         self.mpsChart.updatePlot(60000, self.mps());
         self.counterChart.updatePlot(60000, self.counter());
      }

      self.popupBuild = function() {
         builder.load(self.entityId);
      }

      var LogConsts = app.server.consts["Core.ServiceLogEntry"];

      var levels = [LogConsts.LEVEL_ALL, LogConsts.LEVEL_TRACE, LogConsts.LEVEL_DEBUG, LogConsts.LEVEL_INFO, LogConsts.LEVEL_WARN, LogConsts.LEVEL_ERROR, LogConsts.LEVEL_OFF];

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

      var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
      function logtime(d) {
         return d.getFullYear() + "-" + months[d.getMonth()] + "-" + d.getDate() + " " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds() + ":" + d.getMilliseconds();
      }
      self.logLevel = ko.observable(LogConsts.LEVEL_INFO);
      self.logs = ko.observableArray([]);
      self.autoScrollLogs = ko.observable(true);
      var logRefreshPending = false;

      self.refreshLogs = function() {
         if (!logRefreshPending) {
            logRefreshPending = true;

            app.server.sendTo("ServiceLogs", {
               logId: self.lastLogId,
               level: self.logLevel(),
               maxItems: 100
            }, self.entityId, function(res) {
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
