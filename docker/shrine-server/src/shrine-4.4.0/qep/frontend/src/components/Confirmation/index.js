import React from "react";
import PropTypes from "prop-types";

import Button from "@material-ui/core/Button";
import Dialog from "@material-ui/core/Dialog";
import DialogContent from "@material-ui/core/DialogContent";
import DialogActions from "@material-ui/core/DialogActions";
import DialogContentText from "@material-ui/core/DialogContentText";

export default function Confirmation({ onOk, onCancel, text }) {
  return (
    <Dialog
      open
      onClose={onCancel}
      aria-labelledby="alert-dialog-description"
    >
      <DialogContent>
        <DialogContentText id="alert-dialog-description">
          {text}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onOk} color="primary">
          Yes
        </Button>
        <Button onClick={onCancel} color="primary">
          Cancel
        </Button>
      </DialogActions>
    </Dialog>
  );
}

Confirmation.propTypes = {
  onOk: PropTypes.func.isRequired,
  text: PropTypes.string.isRequired,
  onCancel: PropTypes.func.isRequired
};
