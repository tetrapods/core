import * as React from "react";
import TextField from 'material-ui/TextField';
import {Property} from "./model/Property";
import {chatboxContext} from "../ChatboxContext";
import {DialogCallback} from "../util/DialogState";

interface EditPropertyDialogState {
   value: string;
}

interface EditPropertyDialogProps {
   property: Property;
}

export class EditPropertyDialog extends React.Component<EditPropertyDialogProps, EditPropertyDialogState> implements DialogCallback {

   constructor(props: EditPropertyDialogProps, context: any) {
      super(props, context);
      this.state = {value: props.property.val};
   }

   render() {
      return (
         <div>
            Enter a new value for <b>{this.props.property.key}</b>
            <br/>
            <TextField id="prop-edit-field" value={this.state.value} onChange={this.handleChange}/>
         </div>
      );
   }

   handleChange = (event: any) => {
      this.setState({value: event.target.value});
   };

   submit(callback: ((result: any) => void)) {
      chatboxContext.sendAny("SetClusterProperty", {
         property: {
            key: this.props.property.key,
            val: this.state.value,
            secret: this.props.property.secret
         }
      }, callback);
   }

}
