import * as React from "react";
import {chatboxContext} from "../ChatboxContext";
import {TabProps} from "../App";
import {Property} from "./model/Property"
import Map from "../util/Map"
import {Table, TableBody, TableHeader, TableHeaderColumn, TableRow, TableRowColumn} from 'material-ui/Table';
import RaisedButton from 'material-ui/RaisedButton';
import FlatButton from 'material-ui/FlatButton';
import IconButton from 'material-ui/IconButton';
import FontIcon from 'material-ui/FontIcon';
import Dialog from 'material-ui/Dialog';
import './Properties.scss';
import DialogState from "../util/DialogState";
import {EditPropertyDialog} from "./EditPropertyDialog";
import {AddPropertyDialog} from "./AddPropertyDialog";
import {DialogCallback} from "../util/DialogState";
import {DeletePropertyDialog} from "./DeletePropertyDialog";

enum DialogType {
   DELETE,
   EDIT,
   ADD,
   IMPORT,
   ERROR
}

interface PropertiesState {
   dialogOpen: boolean;
}

export default class Properties extends React.Component<TabProps, PropertiesState> {

   private propertyMap: Map<Property>;
   private properties: Array<Property> = [];
   private dialogState: DialogState<Property, DialogType>;

   constructor(props: TabProps, context: PropertiesState) {
      super(props, context);
      this.propertyMap = new Map<Property>();
      this.dialogState = new DialogState<Property, DialogType>();
      this.state = {dialogOpen: false};
      chatboxContext.addMessageHandler("ClusterPropertyAdded", this.propertyAdded);
      chatboxContext.addMessageHandler("ClusterPropertyRemoved", this.propertyRemoved);
   }

   private propertyRemoved = (msg: any) => {
      this.propertyMap.delete(msg.key);

      // if (p.key == "maintenanceMode") {
      //    app.cluster.hosts.maintenanceMode(false);
      // }
      this.properties = this.propertyMap.values().sort(Property.compareProperties);
      this.forceUpdate();
   };

   private propertyAdded = (msg: any) => {
      this.propertyMap.set(msg.property.key, new Property(msg.property));

      // if (msg.property.key == "maintenanceMode") {
      //    chatboxContext.cluster.hosts.maintenanceMode(true);
      // } else if (msg.property.key == "identity.valid.builds") {
      //    chatboxContext.cluster.hosts.currentClientBuild(msg.property.val);
      // }
      this.properties = this.propertyMap.values().sort(Property.compareProperties);
      this.forceUpdate();
   };

   alertResponse = (result: any) => {
      if (result.isError()) {
         let err = chatboxContext.getErrorStrings(result.errorCode);
         console.warn(err);
         this.dialogState.type = DialogType.ERROR;
         this.dialogState.title = "ERROR";
         this.dialogState.body = <span>{err}</span>;
         this.dialogState.item = undefined;
         this.forceUpdate();
      } else {
         this.setState({dialogOpen: this.dialogState.close()});
      }
   };

   handleOk = () => {
      if (this.dialogState.type == DialogType.ERROR) {
         this.setState({dialogOpen: this.dialogState.close()});
      } else {
         this.dialogState.submit(this.alertResponse);
      }
   };

   handleCancel = () => {
      this.setState({dialogOpen: this.dialogState.close()});
   };

   render() {
      let {} = this.props;

      return (
         <div>
            <br/>
            <RaisedButton className="property-button" label="Add Property" secondary={true}
                          onTouchTap={()=>this.addProperty()}
                          icon={<FontIcon className="fa fa-fw fa-plus" />}/>
            <RaisedButton className="property-button" label="Import" secondary={false}
                          onTouchTap={()=>this.importProperties()}
                          icon={<FontIcon className="fa fa-fw fa-cloud-upload" />}/>
            <Table selectable={false}>
               <TableHeader displaySelectAll={false}>
                  <TableRow>
                     <TableHeaderColumn>Key</TableHeaderColumn>
                     <TableHeaderColumn>Value</TableHeaderColumn>
                  </TableRow>
               </TableHeader>
               <TableBody displayRowCheckbox={false} stripedRows={false}>
                  {this.properties.map((property) =>
                     <TableRow key={property.key}>
                        <TableRowColumn>
                           <IconButton iconClassName="fa fa-fw fa-trash" onTouchTap={()=>this.deleteProperty(property.key)}/>
                           {property.key}
                        </TableRowColumn>
                        <TableRowColumn>
                           <IconButton iconClassName="fa fa-fw fa-edit" onTouchTap={()=>this.editProperty(property.key)}/>
                           {property.secret ? <FontIcon className="fa fa-fw fa-lock"/> : property.val}
                        </TableRowColumn>
                     </TableRow>
                  )}
               </TableBody>
            </Table>
            <Dialog title={this.dialogState.title} modal={true} open={this.state.dialogOpen} onRequestClose={this.handleCancel}>
               {this.dialogState.body}
               <br/>
               {this.dialogState.type == DialogType.ERROR ? undefined :
                  <FlatButton
                     label="Cancel"
                     primary={false}
                     onTouchTap={this.handleCancel}
                  />
               }
               <FlatButton
                  label="Ok"
                  primary={true}
                  keyboardFocused={true}
                  onTouchTap={this.handleOk}
               />
            </Dialog>
         </div>
      );
   }

   private deleteProperty(key: string) {
      const p = this.propertyMap.get(key);
      if (p) {
         const title = 'Delete Property';
         const body = <DeletePropertyDialog property={p} ref={(dc:DialogCallback) => {this.dialogState.dialogCallback=dc}} />;
         this.setState({dialogOpen: this.dialogState.open(title, body, p, DialogType.DELETE)});
      }
   }

   private editProperty(key: string) {
      const p = this.propertyMap.get(key);
      if (p) {
         const title = 'Edit Property';
         const body = <EditPropertyDialog property={p} ref={(dc:DialogCallback) => {this.dialogState.dialogCallback=dc}} />;
         this.setState({dialogOpen: this.dialogState.open(title, body, p, DialogType.EDIT)});
      }
   }

   private addProperty() {
      const title = 'Add Property';
      const body = <AddPropertyDialog ref={(dc:DialogCallback) => {this.dialogState.dialogCallback=dc}} />;
      this.setState({dialogOpen: this.dialogState.open(title, body, undefined, DialogType.ADD)});
   }

   private importProperties() {
      const title = 'Import Property File';
      const body = undefined;
      this.setState({dialogOpen: this.dialogState.open(title, <div/>, undefined, DialogType.IMPORT)});
   }
}
