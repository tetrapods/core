define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");

   return WebRoots; // not using new means this returns a constructor function (ie class)

   function WebRoots(app) {
      var self = this;

      self.webroots = ko.observableArray([]);

      self.addWebRoot = addWebRoot;
      self.findWebRoot = findWebRoot;
      self.clear = clear;

      function clear() {
         self.webroots.removeAll();
      }

      function findWebRoot(name) {
         var arr = self.webroots();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].name == name) {
               return arr[i];
            }
         }
         return null;
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

      function WebRoot(def) {
         var self = this;

         self.name = def.name;
         self.path = def.path;
         self.file = ko.observable(def.file);

         self.editPath = editPath;
         self.editLocation = editLocation;
         self.deleteWebRoot = deleteWebRoot;

         function editPath() {
            Alert.prompt("Enter a new path", function(val) {
               if (val && val.trim().length > 0) {
                  app.server.sendDirect("SetWebRoot", {
                     adminToken: app.authtoken,
                     def: {
                        name: self.name,
                        path: val.trim(),
                        file: self.file()
                     }
                  }, app.server.logResponse);
               }
            }, self.path);
         }

         function editLocation() {
            Alert.prompt("Enter a new location", function(val) {
               if (val && val.trim().length > 0) {
                  app.server.sendDirect("SetWebRoot", {
                     adminToken: app.authtoken,
                     def: {
                        name: self.name,
                        path: self.path,
                        file: val.trim()
                     }
                  }, app.server.logResponse);
               }
            }, self.file());
         }

         function deleteWebRoot() {
            Alert.confirm("Are you sure you want to delete '" + self.name + "'?", function() {
               app.server.sendDirect("DelWebRoot", {
                  adminToken: app.authtoken,
                  name: self.name
               }, app.server.logResponse);
            });
         }
      }

   }

});