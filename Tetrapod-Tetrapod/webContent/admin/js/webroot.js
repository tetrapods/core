/**
 * Wrapper for a WebRootDef object
 */
define(["knockout", "jquery", "bootbox", "app", "alert"], function(ko, $, bootbox, app, Alert) {

   return WebRoot; // not using new means this returns a constructor function (ie class)

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
});