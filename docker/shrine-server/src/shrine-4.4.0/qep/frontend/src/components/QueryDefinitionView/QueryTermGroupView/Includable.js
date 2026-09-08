import React from "react";
import PropTypes from "prop-types";

import "./Includable.scss";

export const Includable = ({ children }) => (
  <div className="Includable">{children}</div>
);

Includable.propTypes = {
  children: PropTypes.element.isRequired,
};
