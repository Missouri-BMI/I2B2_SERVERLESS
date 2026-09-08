import React from "react";
import { Tooltip, Typography } from "@material-ui/core";

export const DisableRangeOccurrenceHelpTooltip = () => {
  return (
    <span>
      <Tooltip
        classes={{ tooltip: "shrine-tooltip" }}
        title={
          <Typography>Disabled. Contains demographics concepts.</Typography>
        }
        arrow
      >
        <div className="info-wrapper">
          <i className="fa fa-info-circle info" />
        </div>
      </Tooltip>
    </span>
  );
};
