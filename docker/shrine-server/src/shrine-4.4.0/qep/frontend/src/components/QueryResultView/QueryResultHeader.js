import React from "react";
import PropTypes from "prop-types";

import { Typography, Button, Divider, Tooltip } from "@material-ui/core";
import "./QueryResultHeader.scss";

export const QueryResultHeader = ({ disableButton, onReloadQueryClick }) => {
  const viewCriteriaText = "Reload the criteria into the Find Patients tab";

  return (
    <div className="QueryResultHeader query-result-flex-container">
      <div>
        <Typography className="section-title">
          View Patient Count by Sites
        </Typography>
        <div />
        <Tooltip
          title={<Typography>{viewCriteriaText}</Typography>}
          classes={{ tooltip: "reload-tooltip" }}
          placement="bottom-start"
          enterDelay={1000}
          arrow
        >
          <Button
            className="details"
            variant="outlined"
            disabled={disableButton}
            size="small"
            onClick={onReloadQueryClick}
          >
            <Typography className="query-result-panel-text">
              Edit Criteria
            </Typography>
          </Button>
        </Tooltip>
      </div>
      <Divider variant="middle" />
    </div>
  );
};

QueryResultHeader.propTypes = {
  disableButton: PropTypes.bool.isRequired,
  onReloadQueryClick: PropTypes.func.isRequired,
};
