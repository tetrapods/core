import * as React from 'react';
import './App.scss';
import {chatboxContext} from './ChatboxContext';

interface TheProps {
   callback?:(error?:string) => void;
}

interface TheState {
   email: string;
   password: string;
}

export default class Login extends React.Component<TheProps, TheState> {

   constructor(props: TheProps, state: TheState) {
      super(props, state);
      this.state = {email: "", password: ""};
   }

   render() {
      return (
         <div className="Login">
            <label>
               Email
               <input name="email" value={this.state.email} type="text" onChange={this.handleInputChange}/>
            </label>
            <label>
               Password
               <input name="password" value={this.state.password} type="password" onChange={this.handleInputChange}/>
            </label>
            <button onClick={this.signIn}>Sign in</button>
         </div>
      );
   }

   handleInputChange = (event: any) => {
      let newState = this.state;
      const target = event.target;
      const value = target.type === 'checkbox' ? target.checked : target.value;
      const name = target.name;
      newState[name] = value;
      this.setState(newState);
   };

   signIn = () => {
      chatboxContext.login(this.state.email, this.state.password, this.props.callback);
   }
}
