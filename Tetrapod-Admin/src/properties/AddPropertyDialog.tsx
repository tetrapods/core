import * as React from "react";
import TextField from 'material-ui/TextField';
import Checkbox from 'material-ui/Checkbox';
import {chatboxContext} from "../ChatboxContext";
import {DialogCallback} from "../util/DialogState";

interface AddPropertyDialogProps {
}

interface AddPropertyDialogState {
   key: string;
   value: string;
   secret: boolean;
}

export class AddPropertyDialog extends React.Component<AddPropertyDialogProps, AddPropertyDialogState> implements DialogCallback {

   constructor(props: AddPropertyDialogProps, context: any) {
      super(props, context);
      this.state = {key:"", value:"", secret:false};
   }

   render() {
      return (
         <div>
            New property key <TextField id="prop-add-key" value={this.state.key} onChange={this.handleChange} name="key"/>
            <br/>
            New property value <TextField id="prop-add-val" value={this.state.value} onChange={this.handleChange} name="value"/>
            <br/>
            <Checkbox checked={this.state.secret} label="Secret" onCheck={this.handleCheck} />
         </div>
      );
   }

   handleChange = (event: any) => {
      this.setState({[event.target.name]: event.target.value});
   };

   handleCheck = (event: any, isInputChecked:boolean) => {
      this.setState({secret: isInputChecked});
   };

   submit(callback: ((result: any) => void)) {
      chatboxContext.sendAny("SetClusterProperty", {
         property: {
            key: this.state.key,
            val: this.state.value,
            secret: this.state.secret
         }
      }, callback);
   }
}
