require.config({
   shim: {
     "jquery.flot": { exports: "$.plot", deps: ["jquery"] },
     "bootstrap": ["jquery"],
     "bootbox": { exports: "bootbox", deps: ["jquery"] }
   },
   paths : {
      "knockout" : "../ext/knockout-3.2.0",
      "knockout-amd-helpers" : "../ext/knockout-amd-helpers",
      "jquery" : "../ext/jquery.min",
      "bootstrap" : "../ext/bootstrap.min",
      "bootbox" : "../ext/bootbox.min",
      "sweet-alert" : "../ext/sweet-alert",
      "jquery.flot" : "//cdnjs.cloudflare.com/ajax/libs/flot/0.8.2/jquery.flot.min",
      "protocol" : "../../protocol"
   }
});

require(["app", "cluster", "bootstrap", "jquery", "jquery.flot"], function(app, model) {
   setTimeout(function() {
      app.run(model);
   }, 0);
});