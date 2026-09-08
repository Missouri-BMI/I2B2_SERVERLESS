import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import {
  Dialog,
  Button,
  InputLabel,
  Typography,
  Backdrop,
} from "@material-ui/core";

import { Fav } from "models";
import { ValidatedTextField } from "components";
import "./FavDialog.scss";

export const FavDialog = ({
  open,
  onClose,
  currentFav,
  qid,
  favTexts,
}) => {
  const faved = currentFav && currentFav.faved;

  const defaultFavingInstructions = faved
    ? "Unflag as a favorite or update the reason for which this query is a favorite."
    : "Enter the reason for which this query is a favorite.";
  const favInstructions =
    favTexts.queryFavingInstructions || defaultFavingInstructions;
  const favPlaceholderText =
    favTexts.favPlaceholderText || "Enter a message...";

  const [canSubmit, setCanSubmit] = useState(false);
  const [favMessage, setFavMessage] = useState(
    currentFav ? currentFav.favMessage : ""
  );

  const resetFavMessage = () => {
    if (currentFav) {
      setFavMessage(currentFav.favMessage);
    } else {
      setFavMessage("");
    }
  };

  useEffect(() => {
    resetFavMessage();
  }, [currentFav]);

  const handleCancel = () => {
    resetFavMessage();
    onClose(false);
  };
  const handleUnfav = () => {
    setFavMessage("");
    onClose(true, false, null);
  };
  const handleFav = () => {
    if (canSubmit) {
      onClose(true, true, favMessage);
    }
  };

  return (
    <Dialog
      aria-labelledby="spring-modal-title"
      aria-describedby="spring-modal-description"
      className="FavDialog"
      open={open}
      onClose={onClose}
      closeAfterTransition
      BackdropComponent={Backdrop}
      BackdropProps={{
        timeout: 500,
      }}
    >
      <div className="DialogContent">
        <span className="close-button" onClick={handleCancel} role="button">
          <i className="fa fa-times" aria-hidden="true" />
        </span>
        <div className="title-label-wrapper">
          <Typography className="title">Favorite Status</Typography>
        </div>
        <div className="name-label-wrapper">
          <InputLabel>{favInstructions}</InputLabel>
        </div>
        <div className="qid-label">
          <InputLabel>{`Query ID: ${qid}`}</InputLabel>
        </div>
        <div>
          <ValidatedTextField
            label="Message for why this query is a favorite"
            onTextChange={handleTextChange}
            multiline
            maxTextLength={1000}
            // we want to allow all characters so do not set invalidCharsRegex
            // invalidCharsRegex="[^ !-~\n]"
            // Note: \n characters are allowed by virtue of the "multiline" property above
            // TODO-XH : do we want to reuse description-input here, or have its own class name?
            className="description-input"
            autoFocus
            placeholder={favPlaceholderText}
            fullWidth
            value={favMessage}
          />
        </div>
        <div className="button-row">
          {faved && (
            <Button className="btn-primary" onClick={handleUnfav}>
              not a Favorite
            </Button>
          )}
          <Button
            onClick={handleFav}
            className="btn-primary"
            disabled={!canSubmit}
          >
            {faved ? "Update" : "Favorite"}
          </Button>
        </div>
      </div>
    </Dialog>
  );
};

FavDialog.defaultProps = {
  currentFav: null,
};

FavDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  currentFav: PropTypes.shape(Fav.propTypes),
  qid: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  favTexts: PropTypes.shape({
    queryFavingInstructions: PropTypes.string,
    favPlaceholderText: PropTypes.string,
  }).isRequired,
};
