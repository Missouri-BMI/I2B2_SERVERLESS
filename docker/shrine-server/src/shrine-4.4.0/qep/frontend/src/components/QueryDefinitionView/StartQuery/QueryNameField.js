import {
  IconButton,
  InputAdornment,
  Tooltip,
  Typography,
} from "@material-ui/core";
import React, { useState } from "react";
import PropTypes from "prop-types";
import { ValidatedTextField } from "components";
import "./StartQuery.scss";

export default function QueryNameField(props) {
  const { onTextChange, value, onGenerateName, ...muiProps } = props;
  const generateAdornment = (
    <InputAdornment position="end">
      <Tooltip
        classes={{ tooltip: "shrine-tooltip" }}
        title={<Typography>Autogenerate name</Typography>}
        arrow
      >
        <IconButton
          className="generate fa-solid fa-bolt-lightning"
          role="button"
          onClick={onGenerateName}
        />
      </Tooltip>
    </InputAdornment>
  );

  return (
    <ValidatedTextField
      {...muiProps}
      value={value}
      onTextChange={onTextChange}
      InputProps={{
        endAdornment: generateAdornment,
      }}
    />
  );
}

QueryNameField.defaultProps = {
  onTextChange: null,
};

QueryNameField.propTypes = {
  onTextChange: PropTypes.func,
  value: PropTypes.string.isRequired,
  onGenerateName: PropTypes.func,
};
