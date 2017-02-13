import * as React from 'react';
import * as ReactDOM from 'react-dom';
import App from './App';
import './index.css';
import * as injectTapEventPlugin from 'react-tap-event-plugin';

injectTapEventPlugin();
ReactDOM.render(
  <App />,
  document.getElementById('root') as HTMLElement
);
