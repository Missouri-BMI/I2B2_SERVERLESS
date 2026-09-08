import React, { useRef, useState, useEffect, useContext } from "react";
import PropTypes from "prop-types";
import { TableRow, TableCell, Tooltip, Typography } from "@material-ui/core";

import "./ResultRow.scss";
import { getTimestamp } from "../../utilities";
import { QueryResult } from "../../models";
import { FavDialog } from "./FavDialog";
import { QueryNameAndNotesDialog } from "./QueryNameAndNotesDialog";
import { QueryHistoryContext } from "./QueryHistoryContext";

export const ResultRow = ({ result }) => {
  const {
    loadResult: onClick,
    handleFavQuery,
    handleRenameQuery,
    selectedQueryId
  } = useContext(QueryHistoryContext);

  const [faved, setFaved] = useState(!!result && !!result.queryFaved);
  const [openRenameQuery, setOpenRenameQuery] = useState(false);

  const rowSelected = selectedQueryId === result.queryId;
  const selectedClass = rowSelected ? "selected" : "";
  const updatedClass = result.observed ? "" : "updated";
  const favClassNames =
    faved
      ? "fa fa-star hover-controls faved"
      : "fa fa-star-o hover-controls";

  const statusClassName = result.isError
    ? "error"
    : result.isQueryComplete
    ? "complete"
    : "pending";

  const statusTooltipText = result.isError
    ? "Error"
    : result.isQueryComplete
    ? "Completed"
    : "In Progress";

  // Logic to check if the query name text is overflowing
  const [overflow, setOverflow] = useState(false);
  const nameRef = useRef();
  const checkOverflow = () => {
    const isOverflow =
      nameRef.current.scrollWidth > nameRef.current.clientWidth;
    setOverflow(isOverflow);
  };
  useEffect(() => {
    checkOverflow();
    window.addEventListener("resize", checkOverflow);
  }, []);
  useEffect(
    () => () => {
      window.removeEventListener("resize", checkOverflow);
    },
    []
  );

  const handleClick = () => {
    onClick(result.queryId, result.queryName);
  };

  const handleStarClicked = () => {
    handleFavQuery(result.queryId, !faved);
    setFaved(!faved);
  };

  const handleOpenRenameQuery = () => {
    setOpenRenameQuery(true);
  };
  const handleCloseRenameQuery = (submit, queryName, queryNotes) => {
    setOpenRenameQuery(false);
    if (submit === true) {
      handleRenameQuery(result.queryId, queryName, queryNotes);
    }
  };

  return (
    <TableRow
      className={`ResultRow ${updatedClass} ${selectedClass}`}
      onClick={handleClick}
    >
      <TableCell>
        <Tooltip
          title={<Typography>{statusTooltipText}</Typography>}
          classes={{ tooltip: "result-row-tooltip" }}
          enterDelay={1000}
          arrow
        >
          <span className="fa-stack status-icons">
            <i
              className={"fa fa-circle fa-stack-1x " + statusClassName}
              aria-hidden="true"
            />
            <i
              className={"fa fa-circle-thin fa-stack-1x " + statusClassName}
              aria-hidden="true"
            />
            <i
              className={"fa fa-exclamation-circle " + statusClassName}
              hidden={!result.isError}
              aria-hidden="true"
            />
          </span>
        </Tooltip>
      </TableCell>
      <Tooltip
        title={<Typography>{result.queryName}</Typography>}
        classes={{ tooltip: "result-row-tooltip" }}
        disableHoverListener={!overflow}
        enterDelay={1000}
        arrow
      >
        <TableCell className="queryHistoryName" ref={nameRef}>
          {result.queryName}
        </TableCell>
      </Tooltip>
      <TableCell>{getTimestamp(result.dateCreated)}</TableCell>
      <TableCell>
        <i
          className={favClassNames}
          aria-hidden="true"
          onClick={handleStarClicked}
        />
      </TableCell>
      <TableCell>
        <i
          className="fa fa-pencil hover-controls"
          aria-hidden="true"
          onClick={handleOpenRenameQuery}
        />
      </TableCell>
      <QueryNameAndNotesDialog
        open={openRenameQuery}
        qid={result.queryId}
        onClose={handleCloseRenameQuery}
        currentQueryName={result.queryName}
        currentQueryNotes={result.queryNotes}
      />
    </TableRow>
  );
};

ResultRow.propTypes = {
  result: PropTypes.shape(QueryResult.propTypes).isRequired
};
