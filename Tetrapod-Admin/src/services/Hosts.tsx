import * as React from "react";
import {chatboxContext} from "../ChatboxContext";
import HostTable from "./HostTable";
import {HostMap} from "./model/HostMap";
import {TabProps} from "../App";

interface HostsState {
}


export default class Hosts extends React.Component<TabProps, HostsState> {

   private hostmap:HostMap;

   constructor(props: TabProps, context: HostsState) {
      super(props, context);
      this.hostmap = new HostMap();
      chatboxContext.addMessageHandler("ServiceAdded", this.serviceAdded);
      chatboxContext.addMessageHandler("ServiceAdded", this.serviceUpdated);
      chatboxContext.addMessageHandler("ServiceAdded", this.serviceRemoved);
      chatboxContext.addMessageHandler("ServiceStats", this.serviceStats);
      chatboxContext.addMessageHandler("NagiosStatus", this.nagiosStatus);
   }

   private serviceAdded = (msg: any) => {
      this.hostmap.addService(msg);
      this.forceUpdate();
   };

   private serviceUpdated = (msg: any) => {
      let service = this.hostmap.findService(msg.entityId);
      if (service) {
         let wasGone = service.isGone();
         service.status = msg.status;
         // resub when service returns
         if (wasGone && !service.isGone()) {
            service.subscribe(1);
         }
      }
      this.forceUpdate();
   };

   private serviceRemoved = (msg: any) => {
      this.hostmap.removeService(msg.entityId);
      this.forceUpdate();
   };

   private serviceStats = (msg: any) => {
      let s = this.hostmap.findService(msg.entityId);
      if (s) {
         s.statsUpdate(msg);
      }
      this.forceUpdate();
   };

   private nagiosStatus = (msg: any) => {
      let host = this.hostmap.findHost(msg.hostname);
      if (host) {
         host.nagios = msg.enabled;
      }
      this.forceUpdate();
   };

   render() {
      let {} = this.props;

      return (
         <div>
            {this.hostmap.hosts().map((host) =>
               <HostTable key={host.hostname} host={host}/>
            )}
         </div>
      );
   }
}
