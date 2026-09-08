import React from "react";
import PropTypes from "prop-types";

import "./ToggleContainer.scss";

export default function ToggleContainer({ isOpen, children, onClose }) {
  const openClass = isOpen ? "open" : "";

  return (
    <div className={`ToggleContainer ${openClass}`}>
      <div
        role="button"
        className="minimize-toggle-container"
        onClick={onClose}
      >
        <i className="fa fa-window-minimize" />
      </div>
      {children}
    </div>
  );
}

ToggleContainer.propTypes = {
  isOpen: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.element,
    PropTypes.arrayOf(PropTypes.element)
  ]).isRequired,
  onClose: PropTypes.func.isRequired
};

ToggleContainer.defaultProps = {
  isOpen: false
};
