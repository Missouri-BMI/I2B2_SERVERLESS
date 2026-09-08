import React from "react";
import PropTypes from "prop-types";
import { TableCell, Typography } from "@material-ui/core";

import { InstitutionResultOptions } from "models";
import "./SortHeaderCell.scss";

const { sortBy } = InstitutionResultOptions;
export function SortHeaderCell(props) {
  const {
    id,
    label,
    isSelected,
    onToggle,
    sortOrder,
    sortIconClass,
    ...muiProps
  } = props;

  return (
    <TableCell className="SortHeaderCell" {...muiProps}>
      <span onClick={onToggle(id)} role="button">
        <Typography>
          {label}
          {isSelected && (
            <i
              className={`fa ${sortIconClass}-${
                sortOrder === sortBy.ASC ? "asc" : "desc"
              }`}
              aria-hidden="true"
            />
          )}
        </Typography>
      </span>
    </TableCell>
  );
}

SortHeaderCell.propTypes = {
  id: PropTypes.string.isRequired,
  label: PropTypes.string.isRequired,
  isSelected: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
  sortOrder: PropTypes.oneOf([sortBy.ASC, sortBy.DESC]).isRequired,
  sortIconClass: PropTypes.string.isRequired
};
