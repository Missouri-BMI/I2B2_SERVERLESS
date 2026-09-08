import React from "react";
import PropTypes from "prop-types";
import { connect } from "react-redux";

import { fetchOntologySearchSuggestions } from "actions";
import { SearchSuggestions } from "models";
import { AutoSuggestView } from "components";

export function WrappedAutoSuggestContainer({ dispatch, autoSuggestions }) {
  const updateSuggestions = (suggestString) => {
    dispatch(fetchOntologySearchSuggestions({ suggestString }));
  };

  return (
    <AutoSuggestView
      onUpdateSuggestions={updateSuggestions}
      autoSuggestions={autoSuggestions}
    />
  );
}

WrappedAutoSuggestContainer.propTypes = {
  dispatch: PropTypes.func.isRequired,
  autoSuggestions: PropTypes.shape(SearchSuggestions.propTypes).isRequired,
};

const mapStateToProps = ({ autoSuggestions }) => ({ autoSuggestions });
const AutoSuggestContainer = connect(mapStateToProps)(
  WrappedAutoSuggestContainer
);
export { AutoSuggestContainer };
