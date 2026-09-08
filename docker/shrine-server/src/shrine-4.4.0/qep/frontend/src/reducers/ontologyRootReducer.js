import {
  FETCH_ONTOLOGY_ROOT,
  FETCH_FILTER_OPTIONS_FAILED,
  FETCH_ONTOLOGY_ROOT_FAILED,
  FETCH_ONTOLOGY_ROOT_SUCCEEDED,
  FETCH_FILTERED_ONTOLOGY_FAILED
} from "actions";

import { Error, Ontology } from "../models";

export const ontologyRootReducer = (state, action) => {
  switch (action.type) {
    case FETCH_ONTOLOGY_ROOT: {
      return Ontology({
        ...state,
        isFetching: true
      });
    }
    case FETCH_FILTER_OPTIONS_FAILED:
    case FETCH_FILTERED_ONTOLOGY_FAILED:
    case FETCH_ONTOLOGY_ROOT_FAILED: {
      const { status, statusText, url } = action.payload;
      const error = Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      });
      return Ontology({
        ...state,
        error,
        isFetching: false
      });
    }

    case FETCH_ONTOLOGY_ROOT_SUCCEEDED: {
      return Ontology({
        ...state,
        isFetching: false,
        root: [...action.payload]
      });
    }

    default: {
      return state;
    }
  }
};
