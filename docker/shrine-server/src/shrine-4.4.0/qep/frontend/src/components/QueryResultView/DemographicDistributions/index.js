import React, { useState, useEffect } from "react";
import { connect } from "react-redux";
import PropTypes from "prop-types";
import { Grid, Tooltip, Typography, IconButton } from "@material-ui/core";

import { Accordion } from "components";
import "./DemographicDistributions.scss";
import { exportDemographicToCSV } from "actions";
import { BarGraphList } from "./BarGraphList";

export const DemographicDistributionsContainer = ({
  demographicData,
  selectedQuery,
  dispatch,
}) => {
  const { csvDownloadURL, queryId, queryResult } = selectedQuery;

  const [csvDataURLValid, setCSVDataURLValid] = useState(false);

  const csvExportName = () => {
    const qName = queryResult.queryName;
    const curDate = new Date(Date.now());
    const date = curDate.toLocaleDateString(undefined, {
      month: "numeric",
      day: "numeric",
      year: "2-digit",
    });
    const time = curDate.toLocaleTimeString(undefined, {
      hour12: false,
    });

    return `${qName.substring(0, 10)}_${date}${time}.csv`;
  };

  const csvLinkRef = React.createRef();

  const exportDemographic = () => {
    dispatch(exportDemographicToCSV(queryId));
    setCSVDataURLValid(true);
  };

  useEffect(() => {
    if (csvDownloadURL && csvDataURLValid) {
      csvLinkRef.current.click();
      URL.revokeObjectURL(csvDownloadURL);
    }
  }, [csvDownloadURL]);

  return (
    <Grid item className="DemographicDistributions">
      <div className="accordion-container">
        <Accordion heading="Data Distribution" renderAsExpanded>
          <Tooltip
            classes={{ tooltip: "shrine-tooltip" }}
            title={
              <Typography>
                Download site specific data distributions
              </Typography>
            }
          >
            <IconButton
              className="fa fa-download result-download"
              role="button"
              onClick={exportDemographic}
            />
          </Tooltip>
          {csvDownloadURL && (
            <a
              ref={csvLinkRef}
              className="downloadCSVLink"
              download={csvExportName()}
              href={csvDownloadURL}
            >
              {" "}
              Download{" "}
            </a>
          )}
          <div className="BarGraphContainer">
            <BarGraphList graphDataList={demographicData} />
          </div>
        </Accordion>
      </div>
    </Grid>
  );
};

DemographicDistributionsContainer.defaultProps = {
  demographicData: [],
};

DemographicDistributionsContainer.propTypes = {
  demographicData: PropTypes.arrayOf(PropTypes.shape({})),
};

const mapStateToProps = ({ selectedQuery }) => ({
  selectedQuery,
});

const DemographicDistributions = connect(mapStateToProps)(
  DemographicDistributionsContainer
);
export { DemographicDistributions };
