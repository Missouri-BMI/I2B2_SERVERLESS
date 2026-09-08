import React from "react";
import PropTypes from "prop-types";
import { MenuItem, Select, Divider } from "@material-ui/core";

import "./TypeSelect.scss";

export default function TypeSelect({ labDetailOptionList, visibilityClass, type, onTypeChange }) {
  return labDetailOptionList.length > 1 && (
    <Select
      className={`inline-input TypeSelect ${visibilityClass}`}
      value={type}
      onChange={onTypeChange}
    >
      {labDetailOptionList}
    </Select>
  );
}

TypeSelect.propTypes = {
  labDetail: PropTypes.shape({
    units: PropTypes.arrayOf(PropTypes.string),
    flagValues: PropTypes.arrayOf(PropTypes.string),
    enumValues: PropTypes.arrayOf(PropTypes.string)
  }).isRequired,
  visibilityClass: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  onTypeChange: PropTypes.func.isRequired
};
