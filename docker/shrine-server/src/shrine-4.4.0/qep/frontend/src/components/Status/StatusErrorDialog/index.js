import React, { useState } from "react";
import { connect } from "react-redux";
import PropTypes from "prop-types";
import { Dialog, Typography } from "@material-ui/core";

import { ErrorDetailInfo } from "models";
import { DefaultContent } from "./DefaultContent";
import { ProblemDigest } from "./ProblemDigest";
import "./StatusErrorDialog.scss";

export function WrappedStatusErrorDialog({ emailTo, open, onClose, data }) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      scroll="paper"
      aria-labelledby="scroll-dialog-title"
      aria-describedby="scroll-dialog-description"
      className="StatusErrorDialog"
    >
      <div className="DialogContent">
        <span className="close-button" onClick={onClose} role="button">
          <i className="fa fa-times" aria-hidden="true" />
        </span>
        <div className="title-label-wrapper">
          <Typography className="title">Site Error Details</Typography>
        </div>
        <br />
        {data.hasProblemDigest ? (
          <ProblemDigest data={data} emailTo={emailTo} />
        ) : (
          <DefaultContent />
        )}
      </div>
    </Dialog>
  );
}

WrappedStatusErrorDialog.defaultProps = {
  emailTo: null
};

WrappedStatusErrorDialog.propTypes = {
  emailTo: PropTypes.string,
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  data: PropTypes.shape(ErrorDetailInfo.propTypes).isRequired
};

export const mapStateToProps = (state) => ({
  emailTo: state.networkConfig.siteAdminEmail
});

const StatusErrorDialog = connect(mapStateToProps)(WrappedStatusErrorDialog);
export { StatusErrorDialog };
