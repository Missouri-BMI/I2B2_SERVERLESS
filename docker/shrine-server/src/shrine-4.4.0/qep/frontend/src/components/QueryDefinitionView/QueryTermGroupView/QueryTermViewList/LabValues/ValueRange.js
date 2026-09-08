import React from "react";
import PropTypes from "prop-types";
import { TextField } from "@material-ui/core";

import "./ValueRange.scss";

export default function ValueRange({
  startValue,
  endValue,
  startLabel,
  endLabel,
  isStartError,
  isEndError,
  visibilityClass,
  onStartChange,
  onEndChange
}) {
  return (
    <>
      <TextField
        type="number"
        label={startLabel}
        error={isStartError}
        className={`inline-input LowValue ${visibilityClass}`}
        value={(startValue === null || startValue === undefined) ? "" : startValue}
        onChange={onStartChange}
        placeholder="value"
        InputLabelProps={{
          shrink: true
        }}
      />

      <span className={`between ${visibilityClass}`}>-</span>
      <TextField
        type="number"
        label={endLabel}
        error={isEndError}
        value={(endValue === null || endValue === undefined) ? "" : endValue}
        className={`inline-input HighValue ${visibilityClass}`}
        onChange={onEndChange}
        placeholder="high value"
        InputLabelProps={{
          shrink: true
        }}
      />
    </>
  );
}

ValueRange.propTypes = {
  startValue: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  endValue: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  startLabel: PropTypes.string.isRequired,
  endLabel: PropTypes.string.isRequired,
  isStartError: PropTypes.bool.isRequired,
  isEndError: PropTypes.bool.isRequired,
  visibilityClass: PropTypes.string.isRequired,
  onStartChange: PropTypes.func.isRequired,
  onEndChange: PropTypes.func.isRequired
};

ValueRange.defaultProps = {
  startValue: null,
  endValue: null
};
