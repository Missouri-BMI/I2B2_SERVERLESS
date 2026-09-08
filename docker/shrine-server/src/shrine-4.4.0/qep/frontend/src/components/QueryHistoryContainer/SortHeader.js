import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { TableCell } from "@material-ui/core";

import "./SortHeader.scss";

export const SortHeader = ({ label, id, isFetching, onSort, selected }) => {
  const [sort, setSort] = useState("asc");

  useEffect(() => {
    if (selected) setSort("desc");
  }, []);

  const handleClick = () => {
    if (!isFetching) {
      const newSort = selected === false || sort === "asc" ? "desc" : "asc";
      setSort(newSort);
      onSort(id, newSort);
    }
  };

  return (
    <TableCell
      className={selected ? "SortHeader selected" : "SortHeader"}
      onClick={handleClick}
    >
      <span>
        {label}
        {selected &&
          (sort === "asc" ? (
            <i className="fa fa-caret-up" aria-hidden="true" />
          ) : (
            <i className="fa fa-caret-down" aria-hidden="true" />
          ))}
      </span>
    </TableCell>
  );
};

SortHeader.propTypes = {
  label: PropTypes.oneOfType([PropTypes.string, PropTypes.element]).isRequired,
  id: PropTypes.string.isRequired,
  isFetching: PropTypes.bool.isRequired,
  onSort: PropTypes.func.isRequired,
  selected: PropTypes.bool.isRequired
};
