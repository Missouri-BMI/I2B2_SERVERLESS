import React, { useState } from "react";
import PropTypes from "prop-types";

import { StatusErrorDialog } from "./StatusErrorDialog";

export function ClickableStatus({ className, statusText, data }) {
  const [open, setOpen] = useState(false);

  const handleOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  return (
    <span className={className}>
      {statusText}
      &nbsp;
      <StatusErrorDialog open={open} onClose={handleClose} data={data} />
      <span role="button" onClick={handleOpen}>
        click for details
      </span>
    </span>
  );
}

ClickableStatus.propTypes = {
  className: PropTypes.string.isRequired,
  statusText: PropTypes.string.isRequired
};
