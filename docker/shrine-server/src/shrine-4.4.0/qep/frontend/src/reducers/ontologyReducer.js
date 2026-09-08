import {
  FETCH_ONTOLOGY_ROOT,
  FETCH_ONTOLOGY_ROOT_FAILED,
  FETCH_ONTOLOGY_ROOT_SUCCEEDED,
  FETCH_FILTERED_ONTOLOGY,
  FETCH_FILTERED_ONTOLOGY_FAILED,
  FETCH_FILTERED_ONTOLOGY_SUCCEEDED,
  REMOVE_ONTOLOGY_FILTER,
  FETCH_FILTER_OPTIONS,
  FETCH_FILTER_OPTIONS_FAILED,
  FETCH_FILTER_OPTIONS_SUCCEEDED
} from "actions";
import { ontologyFilterReducer } from "./ontologyFilterReducer";
import { ontologyRootReducer } from "./ontologyRootReducer";
import { filterOptionsReducer } from "./filterOptionsReducer";
import { defaultState } from "../defaultState";

export const ontologyReducer = (state = defaultState.ontology, action) => {
  switch (action.type) {
    case FETCH_ONTOLOGY_ROOT:
    case FETCH_FILTERED_ONTOLOGY_FAILED:
    case FETCH_ONTOLOGY_ROOT_FAILED:
    case FETCH_FILTER_OPTIONS_FAILED:
    case FETCH_ONTOLOGY_ROOT_SUCCEEDED:
      return ontologyRootReducer(state, action);
    case FETCH_FILTERED_ONTOLOGY:
    case FETCH_FILTERED_ONTOLOGY_SUCCEEDED:
    case REMOVE_ONTOLOGY_FILTER:
      return ontologyFilterReducer(state, action);
    case FETCH_FILTER_OPTIONS:
    case FETCH_FILTER_OPTIONS_SUCCEEDED:
      return filterOptionsReducer(state, action);
    default:
      return state;
  }
};
