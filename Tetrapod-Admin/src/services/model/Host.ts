import {Service} from "./Service";
import {chatboxContext} from '../../ChatboxContext';
import Map from "../../util/Map"

export class Host {

   private _services:Map<Service> = new Map<Service>();

   public services:Array<Service> = [];
   public hostname: string;
   public cores: number;
   public disk: number;
   public memory: number;
   public load: number;
   public nagios: number;

   constructor(hostname: string) {
      this.hostname = hostname;
      // start collecting details
      this.updateHostDetails();
      this.updateHostStats();
   }

   // polls a service from this host for host details
   private updateHostDetails() {
      let s = this.getAvailableService();
      if (s) {
         chatboxContext.sendTo("HostInfo", {}, s.entityId, (result) => {
            if (!result.isError()) {
               this.cores = result.numCores;
            }
         });
         chatboxContext.sendAny("NagiosStatus", {
            hostname: this.hostname,
            toggle: false
         }, (result) => {
            if (!result.isError()) {
               this.nagios = result.enabled;
            }
         });
      } else {
         setTimeout(()=>this.updateHostDetails, 1000);
      }
   }

   // polls a service from this host for host details
   private updateHostStats() {
      let s = this.getAvailableService();
      if (s) {
         chatboxContext.sendTo("HostStats", {}, s.entityId, (result) => {
            if (!result.isError()) {
               this.load = result.load.toFixed(1);
               this.disk = result.disk;
            }
            setTimeout(this.updateHostStats, 5000);
            // self.loadChart.updatePlot(60000, self.load());
            // self.diskChart.updatePlot(60000, self.disk());
         });
      } else {
         setTimeout(()=>this.updateHostStats, 1000);
      }
   }

   // returns any available service running on this host slighlty randomly
   private getAvailableService(): Service|null {
      this.iter++;
      for (let i = 0; i < this.services.length; i++) {
         let index = (i + this.iter) % this.services.length;
         let s = this.services[index];
         if (!s.isGone()) {
            return s;
         }
      }
      return null;
   }
   private iter: number = -1;

   findService(entityId: number) {
      return this._services.get(String(entityId));
   }

   addService(s: Service) {
      this._services.delete(String(s.entityId));
      this._services.set(String(s.entityId), s);
      this.updateSortedServices();
   }

   removeService(s: Service) {
      this._services.delete(String(s.entityId));
      this.updateSortedServices();
   }

   private updateSortedServices() {
      this.services = this._services.values().sort(Service.compareServices);
   }

   diskLabel() {
      let d = this.disk / (1024 * 1024);
      /* mb */
      if (d > 10000) {
         return (d / 1024).toFixed(1) + " gb";
      }
      return d.toFixed(1) + " mb";
   }

   onClearAllErrors() {
      for (let service of this._services.values()) {
         service.clearErrors();
      }
   };

   tetrapodId() {
      for (let service of this._services.values()) {
         if (service.name == 'Tetrapod') {
            return service.entityId;
         }
      }
      return 0;
   }


   toggleAlarm() {
      chatboxContext.sendAny("NagiosStatus", {
         hostname: this.hostname,
         toggle: true
      }, (result) => {
         if (!result.isError()) {
            this.nagios = result.enabled;
         }
      });
   }

   static compareHosts(a: Host, b: Host): number {
      return a.hostname == b.hostname ? 0 : (a.hostname < b.hostname ? -1 : 1);
   }

   upgradeHost() {
      /*
       var buildName = app.cluster.properties.findProperty('build.env');
       if (!buildName) {
       // Alert.error("build.env property is not set");
       return;
       }
       buildName = buildName.val();
       var hostId = tetrapodId();
       Alert.prompt("Upgrade " + hostname + " to build " + buildName + "-#", function (val) {
       if (val && val.trim().length > 0) {
       lastBuild = val;
       Builder.upgradeHost(hostname, hostId, buildName, val, self.services());
       }
       }, lastBuild);
       */
   }

}
