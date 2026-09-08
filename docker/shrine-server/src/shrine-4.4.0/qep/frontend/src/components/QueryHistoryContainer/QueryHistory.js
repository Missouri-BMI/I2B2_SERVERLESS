import React, { useState, useRef } from "react";
import PropTypes from "prop-types";
import {
  Table,
  TableHead,
  TableRow,
  TableCell,
  Grid,
  Typography,
  Tooltip
} from "@material-ui/core";

import "./QueryHistory.scss";
import { Loader } from "components";
import { SortHeader } from "./SortHeader";
import { ResultsBody } from "./ResultsBody";

export const QueryHistory = ({ isFetching, favTexts, onSort }) => {
  const [headerId, setHeaderId] = useState("dateCreated");

  const handleSort = (id, sort) => {
    setHeaderId(id);
    onSort(`${id}.${sort}`);
  };

  const favLabel = (
    <Tooltip
      title={<Typography>{favTexts.favingIconInstructions}</Typography>}
      classes={{ tooltip: "query-history-fav-tooltip" }}
      disableHoverListener={!favTexts.favingIconInstructions}
      enterDelay={1000}
      arrow
    >
      <i className="fa fa-star" aria-hidden="true" />
    </Tooltip>
  );

  return (
    <div className="QueryHistory">
      <Grid container className="title-grid">
        <Typography className="title">Previous Results</Typography>
      </Grid>
      {isFetching && <Loader />}
      <div className="content-wrapper">
        <Table
          className={
            isFetching
              ? "table table-hover table-mc-light-blue loading"
              : "table table-hover table-mc-light-blue"
          }
        >
          <TableHead>
            <TableRow>
              <TableCell>
                <i className="fa fa-circle-o" aria-hidden="true" />
              </TableCell>
              <SortHeader
                id="queryName"
                label="Name"
                isFetching={isFetching}
                selected={headerId === "queryName"}
                onSort={handleSort}
              />
              <SortHeader
                id="dateCreated"
                label="Run Date"
                isFetching={isFetching}
                selected={headerId === "dateCreated"}
                onSort={handleSort}
              />
              <SortHeader
                id="queryFaved"
                label={favLabel}
                isFetching={isFetching}
                selected={headerId === "queryFaved"}
                onSort={handleSort}
              />
              <TableCell>
                <i className="fa fa-pencil" aria-hidden="true" />
              </TableCell>
            </TableRow>
          </TableHead>
          <ResultsBody />
        </Table>
      </div>
    </div>
  );
};

QueryHistory.propTypes = {
  isFetching: PropTypes.bool.isRequired
};
