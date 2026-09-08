import React, { useState, useEffect } from "react";
import { connect } from "react-redux";
import { Tooltip, Typography, IconButton } from "@material-ui/core";

import { exportCountToCSV } from "actions";
import "./CountCSVExport.scss";
import { FetchError } from "components";

export const WrappedCountCSVExport = ({ selectedQuery, dispatch }) => {
  const { countCsvDownloadURL, queryId, queryResult } = selectedQuery;

  const [csvDataURLValid, setCSVDataURLValid] = useState(false);
  const csvLinkRef = React.createRef();

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

    return `${qName.substring(0, 10)}${date}${time}_site_counts.csv`;
  };

  const exportCountToCsv = () => {
    dispatch(exportCountToCSV(queryId));
    setCSVDataURLValid(true);
  };

  useEffect(() => {
    if (countCsvDownloadURL && csvDataURLValid) {
      csvLinkRef.current.click();
      URL.revokeObjectURL(countCsvDownloadURL);
    }
  }, [countCsvDownloadURL]);

  return selectedQuery.error.hasError ? (
    <FetchError error={selectedQuery.error} />
  ) : (
    <div className="CountCSVExport">
      <Tooltip
        title={<Typography>Download site results</Typography>}
        arrow
        classes={{ tooltip: "shrine-tooltip" }}
      >
        <div>
          <IconButton
            onClick={exportCountToCsv}
            className="fa fa-download download-site-results"
          />
          {countCsvDownloadURL && (
            <a
              ref={csvLinkRef}
              className="downloadCSVLink"
              download={csvExportName()}
              href={countCsvDownloadURL}
            />
          )}
        </div>
      </Tooltip>
    </div>
  );
};

const mapStateToProps = ({ selectedQuery }) => ({
  selectedQuery,
});

const CountCSVExport = connect(mapStateToProps)(WrappedCountCSVExport);

export { CountCSVExport };
