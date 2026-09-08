import React, { useContext } from "react";
import { TableBody, Button, TableRow, TableCell } from "@material-ui/core";

import { ResultRow } from "./ResultRow";
import { QueryHistoryContext } from "./QueryHistoryContext";
import "./ResultsBody.scss";

export const ResultsBody = () => {
  const {
    showPageBack,
    showPageForward,
    pageBack,
    pageForward,
    results
  } = useContext(QueryHistoryContext);

  return (
    <TableBody className="ResultsBody">
      {showPageBack && (
        <TableRow className="paging-btn">
          <TableCell colSpan="5">
            <Button onClick={pageBack} className="load-previous-btn">
              Load Previous
            </Button>
          </TableCell>
        </TableRow>
      )}
      {results.map((result) => (
        <ResultRow key={result.queryId} result={result} />
      ))}
      {showPageForward && (
        <TableRow className="paging-btn">
          <TableCell colSpan="5">
            <Button onClick={pageForward} className="load-next-btn">
              Load Next
            </Button>
          </TableCell>
        </TableRow>
      )}
    </TableBody>
  );
};
