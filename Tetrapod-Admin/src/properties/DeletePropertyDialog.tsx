import * as React from "react";
import TextField from 'material-ui/TextField';
import {Property} from "./model/Property";
import {chatboxContext} from "../ChatboxContext";
import {DialogCallback} from "../util/DialogState";

interface DeletePropertyDialogState {
}

interface DeletePropertyDialogProps {
   property: Property;
}

export class DeletePropertyDialog extends React.Component<DeletePropertyDialogProps,DeletePropertyDialogState> implements DialogCallback {

   constructor(props: DeletePropertyDialogProps, context: any) {
      super(props, context);
      this.state = {value: props.property.val};
   }

   render() {
      return (
         <div>Are you sure you want to delete property <b>{this.props.property.key}</b></div>
      );
   }

   submit(callback: ((result: any) => void)) {
      chatboxContext.sendAny("DelClusterProperty", {
         key: this.props.property.key
      }, callback);
   }

}
