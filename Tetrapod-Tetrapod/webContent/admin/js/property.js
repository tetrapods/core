/**
 * Wrapper for a ClusterProperty object
 */
define(["knockout", "jquery", "bootbox", "app", "alert"], function(ko, $, bootbox, app, Alert) {

   return Property; // not using new means this returns a constructor function (ie class)

   // Property Model
   function Property(prop) {
      var self = this;

      self.key = prop.key;
      self.secret = prop.secret;
      self.val = ko.observable(prop.val);

      self.editValue = editValue;
      self.deleteProp = deleteProp;

      function editValue() {
         Alert.prompt("Enter a new value", function(val) {
            if (val && val.trim().length > 0) {
               app.server.sendDirect("SetClusterProperty", {
                  adminToken: app.authtoken,
                  property: {
                     key: self.key,
                     val: val,
                     secret: self.secret
                  }
               }, app.server.logResponse);
            }
         }, self.val());
      }

      function deleteProp() {
         Alert.confirm("Are you sure you want to delete '" + self.key + "'?", function() {
            app.server.sendDirect("DelClusterProperty", {
               adminToken: app.authtoken,
               key: self.key
            }, app.server.logResponse);
         });
      }
   }
});