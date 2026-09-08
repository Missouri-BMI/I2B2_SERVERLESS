import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import {Dialog, Button, Typography, InputLabel} from "@material-ui/core";

import { ValidatedTextField } from "components";
import "./QueryNameAndNotesDialog.scss";

export const QueryNameAndNotesDialog = ({ open, onClose, qid, currentQueryName, currentQueryNotes }) => {
  const [queryNameValid, setQueryNameValid] = useState(false);
  const [queryNotesValid, setQueryNotesValid] = useState(false);
  const [canSubmit, setCanSubmit] = useState(false);
  const [queryName, setQueryName] = useState("");
  const [queryNotes, setQueryNotes] = useState("");

  const resetForm = () => {

    // reset query name
    if (currentQueryName) {
      setQueryName(currentQueryName);
    } else {
      setQueryName("");
    }
    setQueryNameValid(true);
    // reset query notes
    if (currentQueryNotes) {
      setQueryNotes(currentQueryNotes);
    } else {
      setQueryNotes("");
    }
    setQueryNotesValid(true);
  };

  const handleQueryNameChange = (textValue, textValid) => {
    setQueryName(textValue);
    setQueryNameValid(textValid)
    updateCanSubmit();
  };

  const handleQueryNotesChange = (textValue, textValid) => {
    setQueryNotes(textValue);
    setQueryNotesValid(textValid);
    updateCanSubmit();
  };

  const updateCanSubmit = () => {
      setCanSubmit(
          (queryNameValid && queryName !== currentQueryName)
              || (queryNotesValid && queryNotes !== currentQueryNotes)
      );
  }

  const handleCancel = () => {
    resetForm();
    onClose(false);
  };

  const handleRenameQuery = () => {
    if (canSubmit) {
      onClose(true, queryName, queryNotes);
    }
  };

  useEffect(() => {
    resetForm();
  }, [currentQueryName, currentQueryNotes]);

  return (
    <Dialog className="QueryNameAndNotesDialog" open={open} onClose={handleCancel}>
      <div className="DialogContent">
        <span className="close-button" onClick={handleCancel} role="button">
          <i className="fa fa-times" aria-hidden="true" />
        </span>
        <div className="title-label-wrapper rename-criteria">
          <Typography className="title">Edit Criteria Set Details</Typography>
        </div>
        <div className="qid-label">
          <p>{`Query ID: ${qid}`}</p>
        </div>
        <div>
          <ValidatedTextField
            // Note: \n characters are disallowed by virtue of the "multiline" property being false
            multiline={false}
            autoFocus
            label="Criteria Set Name"
            fullWidth
            value={queryName}
            onTextChange={handleQueryNameChange}
            className="query-name-input" // needed by unit spec
          />
        </div>
        <div>
          <ValidatedTextField
            label="Notes"
            onTextChange={handleQueryNotesChange}
            multiline
            maxTextLength={1000}
            // we want to allow all characters so do not set invalidCharsRegex
            // invalidCharsRegex="[^ !-~\n]"
            // Note: \n characters are allowed by virtue of the "multiline" property above
            // TODO-XH : do we want to reuse description-input here, or have its own class name?
            className="query-notes-input"
            autoFocus
            placeholder="enter notes here"
            fullWidth
            value={queryNotes}
            required={false}
          />
        </div>
        <div className="button-row">
          <Button
            onClick={handleRenameQuery}
            className="btn-primary submit-button"
            disabled={!canSubmit}
          >
            Update
          </Button>
        </div>
      </div>
    </Dialog>
  );
};

QueryNameAndNotesDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  currentQueryName: PropTypes.string.isRequired,
  currentQueryNotes: PropTypes.string
};
