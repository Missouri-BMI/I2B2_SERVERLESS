import PropTypes from "prop-types";

import { Error } from "./Error";

export const Ontology = ({
  terms = new Map(),
  root = [],
  isFetching = false,
  error = Error(),
  textFilter = null,
  filterValue = null,
  filteredTree = null,
  totalHits = null,
  canLoadMoreFilteredResults = true,
  filterIndex = 0,
  searchResultsMetadata = null,
  filterOptions = null
} = {}) => ({
  terms,
  root,
  isFetching,
  error,
  textFilter,
  filterValue,
  filteredTree,
  totalHits,
  canLoadMoreFilteredResults,
  filterIndex,
  searchResultsMetadata,
  filterOptions
});

Ontology.propTypes = {
  textFilter: PropTypes.string,
  filterValue: PropTypes.string,
  terms: PropTypes.instanceOf(Map),
  filteredTree: PropTypes.arrayOf(PropTypes.shape({})),
  filterOptions: PropTypes.arrayOf(PropTypes.shape({
    filterType: PropTypes.string,
    filterValue: PropTypes.string,
    displayableName: PropTypes.string
  })),
  root: PropTypes.arrayOf(PropTypes.shape({})),
  isFetching: PropTypes.bool,
  error: PropTypes.shape(Error.propTypes),
  totalHits: PropTypes.number,
  filterIndex: PropTypes.number,
  canLoadMoreFilteredResults: PropTypes.bool,
  searchResultsMetadata: PropTypes.shape({
    lastDocId: PropTypes.string,
    sortFieldValue: PropTypes.string
  })
};
