import React from 'react';
import PropTypes from 'prop-types';

import './Status.scss';

export const Status = ({ className, statusText }) => (
  <span
    className={className}
  >
    {statusText}
  </span>
);

Status.propTypes = {
  className: PropTypes.string.isRequired,
  statusText: PropTypes.string.isRequired,
};
