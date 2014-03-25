var TP = TP || {};

TP.sidebar = {};

TP.sidebar.items = [
    { 
       title: "Cluster",
       icon: "fa-sitemap",
       children: [
          { url: "cluster.html", title: "Administer" },
          { url: "index.html", title: "Deploy" },
          { url: "index.html", title: "Configuration" }
       ]
    },
    { 
       title: "Users",
       icon: "fa-user",
       children: [
          { url: "index.html", title: "Find Account" },
          { url: "index.html", title: "Review Names" },
          { url: "index.html", title: "Review Images" }
       ]
    },
    { 
       title: "Tournaments",
       icon: "fa-flag",
       children: [
          { url: "index.html", title: "Active" },
          { url: "index.html", title: "Configuration" },
       ]
    },
    { 
       title: "Charts",
       icon: "fa-bar-chart-o",
       children: [
          { url: "index.html", title: "Overview" },
          { url: "index.html", title: "Acquisition" },
          { url: "index.html", title: "Engagement" },
          { url: "index.html", title: "Retention" },
          { url: "index.html", title: "Revenue" },
          { url: "index.html", title: "Other" },
       ]
    },
    
        
    
];


TP.sidebar.applyBindings = function(menutitle, menusubtitle) {
   TP.sidebar.isActive = function(item) {
      if (menutitle == item.title) {
         if (!menusubtitle) return true;
         if (item.children) {
            var L = item.children.length; 
            for (var i=0; i<L; i++) {
               if (item.children[i].title == menusubtitle)
                  return true;
            }
         }
      }
      return false;
   };

   ko.applyBindings(TP.sidebar, document.getElementById('side-menu'));
};

