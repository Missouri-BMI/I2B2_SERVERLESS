import PropTypes from "prop-types";

import { Error } from "./Error";
import { QueryResult } from "./QueryResult";

export const SORT_BY_DATE_CREATED_ASCENDING = "dateCreated.asc";
export const SORT_BY_DATE_CREATED_DESCENDING = "dateCreated.desc";
export const SORT_BY_QUERY_NAME_ASCENDING = "queryName.asc";
export const SORT_BY_QUERY_NAME_DESCENDING = "queryName.desc";
export const AllQueries = ({
  sortBy = SORT_BY_DATE_CREATED_DESCENDING,
  results = [],
  limit = 100,
  skip = 0,
  rowCount = 0,
  error = Error(),
  isFetching = false
} = {}) => ({
  sortBy,
  results,
  skip,
  limit,
  rowCount,
  error,
  isFetching
});

AllQueries.propTypes = {
  sortBy: PropTypes.string.isRequired,
  results: PropTypes.arrayOf(PropTypes.shape(QueryResult.propTypes)),
  skip: PropTypes.number.isRequired,
  limit: PropTypes.number.isRequired,
  rowCount: PropTypes.number.isRequired,
  error: PropTypes.shape(Error.propTypes),
  isFetching: PropTypes.bool.isRequired
};
