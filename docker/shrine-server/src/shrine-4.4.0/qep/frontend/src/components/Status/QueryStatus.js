import React from "react";
import PropTypes from "prop-types";

import { Tooltip, Typography } from "@material-ui/core";
import { SelectedQuery } from "../../models";
import { Status } from "./Status";
import { ClickableStatus } from "./ClickableStatus";
import "./QueryStatus.scss";

export const QueryStatus = ({ selectedQuery }) => {
  const { queryResult, siteCount, patientCount } = selectedQuery;
  const status = queryResult.errorDetailInfo.status || "Submitted";

  let statusType;
  switch (status.toLowerCase()) {
    case "submission error":
    case "network error":
      statusType = (
        <ClickableStatus
          statusText={status}
          className="QueryError"
          data={queryResult.errorDetailInfo}
        />
      );
      break;
    default:
      statusType = <Status statusText={status} className="QueryNonError" />;
  }

  return (
    <span>
      {statusType}
      <span>
        {` - `}
        <b>{siteCount.toLocaleString()}</b>
        {` sites with patients, up to `}
        <b>{patientCount.toLocaleString()}</b>
        {` total patients `}
      </span>
      <Tooltip
        classes={{ tooltip: "query-definition-help-text" }}
        title={
          <Typography>
            Sites reporting “x patients or fewer” are assumed to have a count of zero patients
          </Typography>
        }
        arrow
      >
        <i className="fa fa-info-circle info" />
      </Tooltip>
    </span>
  );
};

QueryStatus.propTypes = {
  selectedQuery: PropTypes.shape(SelectedQuery.props).isRequired
};
