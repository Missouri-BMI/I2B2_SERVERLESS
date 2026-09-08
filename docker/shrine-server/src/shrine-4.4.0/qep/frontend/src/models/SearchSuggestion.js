import PropTypes from "prop-types";

export const SearchSuggestion = ({
  name = null,
  numberOfOccurrences = 0
} = {}) => ({
  name,
  numberOfOccurrences
});

SearchSuggestion.propTypes = {
  name: PropTypes.string,
  numberOfOccurrences: PropTypes.number
};
