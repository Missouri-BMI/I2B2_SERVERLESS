import { connect } from "react-redux";
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { Dialog, Typography } from "@material-ui/core";

import { FetchError } from "components";
import "./UnexpectedError.scss";

export function WrappedUnexpectedError(props) {
  const { hasError } = props;
  const [open, setOpen] = useState(false);

  const handleClose = () => {
    setOpen(false);
  };

  useEffect(() => {
    if (hasError !== open) {
      setOpen(hasError);
    }
  }, [hasError]);

  return (
    open && (
      <Dialog
        open={open}
        onClose={handleClose}
        scroll="paper"
        aria-labelledby="scroll-dialog-title"
        aria-describedby="scroll-dialog-description"
        className="UnexpectedErrorDialog"
      >
        <div className="DialogContent">
          <span className="close-button" onClick={handleClose} role="button">
            <i className="fa fa-times" aria-hidden="true" />
          </span>
          <div className="title-label-wrapper">
            <Typography className="title">
              An Unexpected Error Occurred
            </Typography>
          </div>
          <br />
          <div className="StatusBoxText content">
            <FetchError error={props} />
          </div>
        </div>
      </Dialog>
    )
  );
}

WrappedUnexpectedError.propTypes = {
  hasError: PropTypes.bool.isRequired
};

const mapStateToProps = (props) => {
  return props.selectedQuery.error;
};
const UnexpectedError = connect(mapStateToProps)(React.memo(WrappedUnexpectedError));
export { UnexpectedError };
