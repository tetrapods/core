import * as React from "react";
import './App.scss';
import Login from './Login';
import {chatboxContext} from './ChatboxContext';
import Hosts from './services/Hosts';
import Properties from './properties/Properties';
import MuiThemeProvider from 'material-ui/styles/MuiThemeProvider';
import {Tabs, Tab} from 'material-ui/Tabs'
import Dialog from 'material-ui/Dialog';
import FlatButton from 'material-ui/FlatButton';

interface ChatboxApp {
   userLoggedIn(loggedIn:boolean):void;
}

export interface TabProps {
   parent: App;
   className?: string;
}

interface TheProps {
}

interface TheState {
   loggedIn: boolean;
   showError: boolean;
}

export default class App extends React.Component<TheProps, TheState> implements ChatboxApp {
   private hosts: Hosts;
   private hostsDisplay: any;
   private properties: Properties;
   private propertiesDisplay: any;
   private error: string;

   constructor(props: any, state: any) {
      super(props, state);
      this.state = {loggedIn: false, showError: false};
      this.hostsDisplay = <Hosts parent={this} ref={(h:Hosts)=>{this.hosts=h}} className="HostsPane"/>;
      this.propertiesDisplay = <Properties parent={this} ref={(p:Properties)=>{this.properties=p}} className="PropertiesPane"/>;
      chatboxContext.init();
      chatboxContext.run(this);
   }

   clearAll() {
      //TODO: clear all the models
   }

   callback = (error?: string) => {
      if (error) {
         this.error = error;
         this.setState({loggedIn: false, showError: true});
      } else {
         this.setState({loggedIn: true, showError: false});
      }
   };

   onTouchTap = () => {
      this.setState({showError:false});
   };

   userLoggedIn(loggedIn:boolean):void {
      this.setState({loggedIn: loggedIn});
   }

   render() {
      //should be able to do this but can't /shrug
      const errorAction = <FlatButton label="OK" primary={true} onTouchTap={this.onTouchTap}/>;

      return (
         <MuiThemeProvider>
            <div className="App">
               <div className="header">
                  <h2>Chatbox Admin</h2>
               </div>
               <div>
                  {
                     !this.state.loggedIn ?
                        <Login callback={this.callback}/>
                        :
                        <Tabs>
                           <Tab label="Services">
                              <h2>Cluster Hosts &amp; Services</h2>
                              {this.hostsDisplay}
                           </Tab>
                           <Tab label="Properties">
                              {this.propertiesDisplay}
                           </Tab>
                           <Tab label="Web Roots">
                              <div>
                                 <h2>Tab Two</h2>
                                 <p>
                                    This is another example tab.
                                 </p>
                              </div>
                           </Tab>
                           <Tab label="Users">
                              <div>
                                 <h2>Tab Two</h2>
                                 <p>
                                    This is another example tab.
                                 </p>
                              </div>
                           </Tab>
                        </Tabs>
                  }
               </div>
               <Dialog title="Login Error" modal={true} open={this.state.showError} >
                  {this.error}
                  <br/>
                  <br/>
                  <FlatButton label="OK" primary={true} onTouchTap={this.onTouchTap}/>
               </Dialog>
            </div>
         </MuiThemeProvider>
      );
   }
}
