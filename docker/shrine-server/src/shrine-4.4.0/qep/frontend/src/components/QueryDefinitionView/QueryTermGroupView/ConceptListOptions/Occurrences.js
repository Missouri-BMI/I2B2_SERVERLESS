import React from "react";
import PropTypes from "prop-types";
import { Typography, Icon, TextField } from "@material-ui/core";

import "./Occurrences.scss";

export default function Occurrences({
  occurrences,
  onOccurrencesChange,
  onResetOccurrences,
  error,
  errorText
}) {
  return (
    <div className="Occurrences">
      <div className="pre-value-label">
        <Typography className="occurrences-label">Occurrences: </Typography>
      </div>
      <TextField
        className="occurrences-input"
        value={occurrences}
        onChange={onOccurrencesChange}
        label="Concepts must occur at least"
        error={error}
        helperText={errorText}
      />
      <div className="post-value-label">
        <Typography>times</Typography>
      </div>
      <div className="clear-value">
        <Icon className="fa fa-undo" onClick={onResetOccurrences} />
      </div>
    </div>
  );
}

Occurrences.propTypes = {
  occurrences: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
    .isRequired,
  onOccurrencesChange: PropTypes.func.isRequired,
  onResetOccurrences: PropTypes.func.isRequired,
  error: PropTypes.bool,
  errorText: PropTypes.string
};

Occurrences.defaultProps = {
  error: false,
  errorText: ""
};
