import {
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS,
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_FAILED,
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_SUCCEEDED
} from "actions";

import { SearchSuggestions } from "models";
import { defaultState } from "defaultState";

export const autoSuggestReducer = (
  state = defaultState.autoSuggestions,
  action
) => {
  switch (action.type) {
    case FETCH_ONTOLOGY_SEARCH_SUGGESTIONS: {
      return SearchSuggestions({
        suggestions: state.suggestions,
        isFetching: true
      });
    }
    case FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_FAILED: {
      const { status, statusText, url } = action.payload;
      const error = Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      });
      return SearchSuggestions({
        error,
        isFetching: false
      });
    }
    case FETCH_ONTOLOGY_SEARCH_SUGGESTIONS_SUCCEEDED: {
      return SearchSuggestions({
        suggestions: action.payload,
        isFetching: false
      });
    }
    default: {
      return state;
    }
  }
};
