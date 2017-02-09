import * as React from 'react';
import '../App.scss';
import {Host} from "./model/Host";
import {Table, TableBody, TableHeader, TableHeaderColumn, TableRow, TableRowColumn} from 'material-ui/Table';
import {ServiceRow} from "./ServiceRow";

interface TheProps {
   host: Host;
}

interface TheState {
}

export default class HostTable extends React.Component<TheProps, TheState> {

   constructor(props: TheProps, state: TheState) {
      super(props, state);
   }

   render() {
      return (
         <div className="Host">
            <div>
               {this.props.host.hostname}
            </div>
            <Table>
               <TableHeader>
                  <TableRow>
                     <TableHeaderColumn>Gear</TableHeaderColumn>
                     <TableHeaderColumn>Name</TableHeaderColumn>
                     <TableHeaderColumn>Entity</TableHeaderColumn>
                     <TableHeaderColumn>Build</TableHeaderColumn>
                     <TableHeaderColumn>Status</TableHeaderColumn>
                     <TableHeaderColumn>Latency</TableHeaderColumn>
                     <TableHeaderColumn>RPS</TableHeaderColumn>
                     <TableHeaderColumn>MPS</TableHeaderColumn>
                     <TableHeaderColumn>Counter</TableHeaderColumn>
                  </TableRow>
               </TableHeader>
               <TableBody>
                  {this.props.host.services.map((service) =>
                     <ServiceRow key={service.entityId} service={service}/>
                  )}
               </TableBody>
            </Table>
         </div>
      );
   }

}
