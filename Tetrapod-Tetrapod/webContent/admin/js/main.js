require.config({
   shim: {
      "jquery.flot": {
         exports: "$.plot",
         deps: ["jquery"]
      },
      "bootstrap": ["jquery"],
      "bootbox": {
         exports: "bootbox",
         deps: ["jquery"]
      }
   },
   paths: {
      "knockout": "../ext/knockout-3.3.0",
      "knockout-amd-helpers": "../ext/knockout-amd-helpers",
      "jquery": "../ext/jquery.min",
      "bootstrap": "../ext/bootstrap.min",
      "bootbox": "../ext/bootbox.min",
      "vex": "../ext/vex",
      "vex.dialog": "../ext/vex.dialog",
      "text": "../ext/text",
      "jquery.flot": "//cdnjs.cloudflare.com/ajax/libs/flot/0.8.2/jquery.flot.min",
      "protocol": "../../protocol"
   }
});

require(["app", "cluster", "knockout", "knockout-amd-helpers", "bootstrap", "jquery.flot"], function(app, model, ko) {
   ko.bindingHandlers.module.baseDir = "modules";
   setTimeout(function() {
      app.run(model);
   }, 0);
});
