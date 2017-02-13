import {Service} from "./Service";
import {Host} from "./Host";
import Map from "../../util/Map"

export class HostMap {

   private _services:Map<Service> = new Map<Service>();
   private _hosts:Map<Host> = new Map<Host>();

   addService(msg:any) {
      let hostname = msg.entity.host;

      if(this._services.has(msg.entity.entityId)) {
         this.removeService(msg.entity.entityId);
      }

      let host = this._hosts.get(hostname);
      if (!host) {
         host = new Host(hostname);
         this._hosts.set(hostname, host);
      }

      let service = new Service(msg.entity);
      host.addService(service);
      this._services.set(msg.entity.entityId, service);
   }

   removeService(entityId:number) {
      const service = this._services.delete(String(entityId));
      if(service) {
         const host = this._hosts.get(service.hostname);
         if (host) {
            host.removeService(service);
         }
      }
   }

   findService(entityId: number) {
      return this._services.get(String(entityId));
   }

   findHost(hostname: string) {
      return this._hosts.get(hostname);
   }

   hosts() {
      return this._hosts.values()
   }

}

