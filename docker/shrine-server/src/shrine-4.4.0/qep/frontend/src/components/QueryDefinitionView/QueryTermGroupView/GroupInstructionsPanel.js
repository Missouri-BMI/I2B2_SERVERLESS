import React from "react";
import PropTypes from "prop-types";
import { Grid } from "@material-ui/core";

import "./GroupInstructionsPanel.scss";

export default function GroupInstructionsPanel({ hasChildren, children }) {
  return (
    <Grid
      className="GroupInstructionsPanel"
      container
      direction="row"
      justify="flex-start"
      alignItems="center"
    >
      {!hasChildren && children}
      {hasChildren && (
        <Grid
          container
          direction="row"
          justify="flex-start"
          alignItems="center"
          className="drag-here"
        >
          <div>or drag additional concepts</div>
        </Grid>
      )}
    </Grid>
  );
}

GroupInstructionsPanel.propTypes = {
  hasChildren: PropTypes.bool.isRequired,
};
