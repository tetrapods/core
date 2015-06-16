define(function(require) {
   var $ = require("jquery");
   var ko = require("knockout");
   var Alert = require("alert");
   return Properties; // not using new means this returns a constructor function (ie class)

   function Properties(app) {
      var self = this;

      self.props = ko.observableArray([]);

      self.findProperty = findProperty;

      self.addClusterProperty = addClusterProperty;

      function findProperty(key) {
         var arr = self.props();
         for (var i = 0; i < arr.length; i++) {
            if (arr[i].key == key) {
               return arr[i];
            }
         }
         return null;
      }

      function addClusterProperty(secret) {
         Alert.prompt("Enter a new key name", function(key) {
            if (key && key.trim().length > 0) {
               app.server.sendDirect("SetClusterProperty", {
                  adminToken: app.authtoken,
                  property: {
                     key: key,
                     val: '',
                     secret: secret
                  }
               }, app.server.logResponse);
            }
         });
      }
      app.server.addMessageHandler("ClusterPropertyAdded", function(msg) {
         var p = self.findProperty(msg.property.key);
         if (p) {
            self.props.remove(p);
         }
         p = new Property(msg.property);
         self.props.push(p);
         self.props.sort(compareProperties);
      });

      app.server.addMessageHandler("ClusterPropertyRemoved", function(msg) {
         var p = self.findProperty(msg.key);
         if (p) {
            self.props.remove(p);
         }
      });

      function compareProperties(a, b) {
         return a.key.localeCompare(b.key);
      }
      
      

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
      
      
   }

});