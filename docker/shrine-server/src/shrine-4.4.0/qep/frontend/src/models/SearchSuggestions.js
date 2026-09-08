import PropTypes from "prop-types";

import { Error } from "./Error";
import { SearchSuggestion } from "./SearchSuggestion";

export const SearchSuggestions = ({
  suggestions = [],
  isFetching = false,
  error = Error()
} = {}) => ({
  suggestions,
  isFetching,
  error
});

SearchSuggestions.propTypes = {
  suggestions: PropTypes.arrayOf(PropTypes.shape(SearchSuggestion.propTypes)),
  isFetching: PropTypes.bool,
  error: PropTypes.shape(Error.propTypes)
};
