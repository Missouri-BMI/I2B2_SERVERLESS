import React, { useState } from 'react';

import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  TextField
} from "@material-ui/core";
import './IdleTimerDialog.scss';


export const IdleTimerDialog = ({ open, extendSession, logOut }) => {

  return <Dialog open={open}>
    <DialogTitle id="form-dialog-title">Session Inactivity</DialogTitle>
    <DialogContent>
      <DialogContentText>
          {"You're being timed-out due to inactivity. " +
            "Please choose to stay signed in or to log off. " +
            "Otherwise, you will be logged off automatically."}
      </DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={logOut}>
          {"Log Off"}
          </Button>
      <Button onClick={extendSession}>
        {"Stay Logged In"}
      </Button>
    </DialogActions>
  </Dialog>;
};
