import React from 'react';

import './LeftContentToggleBtn.scss';

import PropTypes from 'prop-types';

export const LeftContentToggleBtn = ({ setOpen, open }) => {
  return (
    <div
      className={open ? 'LeftContentToggleBtn open' : 'LeftContentToggleBtn'}
      role="button"
      onClick={() => setOpen(!open)}
    >
      <span className={open ? 'status btn-close' : 'status'}>
        <span className="bar" />
        <span className="bar" />
        <span className="bar" />
      </span>
    </div>
  );
};

LeftContentToggleBtn.propTypes = {
  setOpen: PropTypes.func.isRequired,
  open: PropTypes.bool.isRequired,
};