import * as React from "react";
import {Service} from "./model/Service";
import {Table, TableBody, TableHeader, TableHeaderColumn, TableRow, TableRowColumn} from 'material-ui/Table';

interface ServiceRowProps {
   service:Service;
}

export class ServiceRow extends React.Component<ServiceRowProps, {}> {

   render() {
      let {service} = this.props;

      return (
         <TableRow>
           <TableRowColumn>X</TableRowColumn>
           <TableRowColumn>{service.name}</TableRowColumn>
           <TableRowColumn>{service.entityId}</TableRowColumn>
           <TableRowColumn>{service.build}</TableRowColumn>
           <TableRowColumn>status</TableRowColumn>
           <TableRowColumn>LATENCY</TableRowColumn>
           <TableRowColumn>RPS</TableRowColumn>
           <TableRowColumn>MPS</TableRowColumn>
           <TableRowColumn>COUNTER</TableRowColumn>
         </TableRow>
      );
   }
}
