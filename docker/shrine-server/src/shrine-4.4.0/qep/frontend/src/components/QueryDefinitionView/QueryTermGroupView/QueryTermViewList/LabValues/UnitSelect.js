import React from "react";
import PropTypes from "prop-types";
import {
  MenuItem,
  Select,
  InputLabel,
  FormControl,
  Typography
} from "@material-ui/core";

import "./UnitSelect.scss";
import "./QueryTermLabView.scss";

export default function UnitSelect({
  value,
  onChange,
  units,
  visibilityClass
}) {
  return (
    <span className="UnitSelect">
      {units && (units.length === 1 ? (
        <Typography className={`unit-label ${visibilityClass}`}>
          {units[0]}
        </Typography>
      ) : (
        <FormControl className={`inline-input ${visibilityClass}`}>
          <InputLabel>Unit</InputLabel>
          <Select
            className="unit-selector"
            InputLabelProps={{
              shrink: true
            }}
            value={value}
            onChange={onChange}
          >
            {units.map(u => (
              <MenuItem key={u} value={u}>
                {u}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      ))}
    </span>
  );
}

UnitSelect.propTypes = {
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  units: PropTypes.arrayOf(PropTypes.string),
  visibilityClass: PropTypes.string.isRequired
};
