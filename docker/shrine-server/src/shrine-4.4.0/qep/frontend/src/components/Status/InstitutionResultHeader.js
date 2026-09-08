import React, { useContext} from "react";
import { TableHead, TableRow, Typography, Tooltip } from "@material-ui/core";

import { InstitutionResultOptions } from "models";
import { QueryResultContext } from "components/QueryResultView/QueryResultContext";
import "./InstitutionResultHeader.scss";
import { SortHeaderCell } from "./SortHeaderCell";

export const InstitutionResultHeader = () => {

  const { sortHeaderId, sortOrder, onSiteResultSort } = useContext(QueryResultContext);

  const handleClickForHeader = (headerId) => () => {
    onSiteResultSort(headerId);
  };

  return (
    <TableHead className="InstitutionResultHeader">
      <TableRow className="institution-table-header">
        <SortHeaderCell
          id="site"
          label="Site"
          onToggle={handleClickForHeader}
          isSelected={sortHeaderId === "site"}
          sortIconClass="fa-sort-alpha"
          sortOrder={sortOrder}
          style={{ width: "60%" }}
        />

        <SortHeaderCell
          id="status"
          label="Status"
          onToggle={handleClickForHeader}
          isSelected={sortHeaderId === "status"}
          sortIconClass="fa-sort-numeric"
          sortOrder={sortOrder}
          style={{ width: "40%" }}
        />
      </TableRow>
    </TableHead>
  );
};
