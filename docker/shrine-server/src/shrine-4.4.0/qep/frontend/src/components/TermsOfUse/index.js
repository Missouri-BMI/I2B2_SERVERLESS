import { connect } from "react-redux";
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  DialogContentText,
} from '@material-ui/core';
import {User, NetworkConfig} from "models";
import {acceptTermsOfUse} from "actions";
import "./TermsOfUse.scss";
import {local} from "../../utilities";

export const WrappedTermsOfUse = ({ dispatch, user, networkConfig }) => {
  const [open, setOpen] = useState(false);

  const handleAcceptTermsOfUse = () => {
    dispatch(acceptTermsOfUse({termsOfUseText: networkConfig.termsOfUseText}));
    setOpen(false);
  };

  useEffect(() => {
    const termsOfUseAcceptedCache = local.isTOUAccepted(networkConfig.termsOfUseText);
    if (user.isAuthenticated && networkConfig.termsOfUseText !== null && !termsOfUseAcceptedCache) {
      setOpen(true);
    }
  }, [networkConfig.termsOfUseText, user.isAuthenticated]);

  return (
    <Dialog
        className="TermsOfUse"
        open={open}
        scroll="paper"
        aria-labelledby="scroll-dialog-title"
        aria-describedby="scroll-dialog-description"
        disableEscapeKeyDown={true}
      >
        <DialogTitle id="scroll-dialog-title">Terms of Use</DialogTitle>
        <DialogContent dividers={true}>
          <DialogContentText
            id="scroll-dialog-description"
            tabIndex={-1}
          >
            <div
              className="tou-text"
              dangerouslySetInnerHTML={{ __html: networkConfig.termsOfUseText }}
            />
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleAcceptTermsOfUse} className="btn-primary btn tou-button">
            I Acknowledge
          </Button>
        </DialogActions>
      </Dialog>
  );
};

WrappedTermsOfUse.propTypes = {
  dispatch: PropTypes.func.isRequired,
  user: PropTypes.shape(User.propTypes).isRequired,
  networkConfig: PropTypes.shape(NetworkConfig.propTypes).isRequired,
};

const mapStateToProps = ({ session, networkConfig }) => ({
  user: session.user,
  networkConfig
});
const TermsOfUse= connect(mapStateToProps)(WrappedTermsOfUse);
export { TermsOfUse };
