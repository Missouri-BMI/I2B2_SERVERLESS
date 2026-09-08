import merge from "deepmerge";

import {
  FETCH_FILTERED_ONTOLOGY,
  FETCH_FILTERED_ONTOLOGY_SUCCEEDED,
  REMOVE_ONTOLOGY_FILTER
} from "actions";
import { Ontology } from "models";
import { defaultState } from "../defaultState";

// TODO: handle this on server instead.
const convertChildrenArraysToMaps = (array = []) => {
  return array.reduce((result, item) => {
    const id = item.path || item.displayName;
    result[id] = Object.assign({}, item, {
      children: convertChildrenArraysToMaps(item.children)
    });
    return result;
  }, {});
};

export const ontologyFilterReducer = (state, action) => {
  switch (action.type) {
    case FETCH_FILTERED_ONTOLOGY: {
      const { searchString: textFilter, filterData: { filterValue } } = action.payload;
      const isNewFilter = textFilter !== state.textFilter || filterValue !== state.filterValue;
      return Ontology({
        ...state,
        isFetching: true,
        textFilter,
        filterValue,
        searchResultsMetadata: isNewFilter
          ? defaultState.searchResultsMetadata
          : state.searchResultsMetadata,
        filteredTree: isNewFilter ? null : state.filteredTree,
        filterIndex: isNewFilter ? 0 : state.filterIndex
      });
    }
    case FETCH_FILTERED_ONTOLOGY_SUCCEEDED: {
      const { filteredTree, filterIndex } = state;
      const { results, searchResultsMetadata, totalHits } = action.payload;
      const flattenedResults = convertChildrenArraysToMaps(results);

      const newFilteredTree = filteredTree
        ? merge(filteredTree, flattenedResults)
        : flattenedResults;
      const newFilterLoadIndex = filterIndex + 1;
      const canLoadMoreFilteredResults = newFilterLoadIndex * 200 < totalHits;

      return Ontology({
        ...state,
        isFetching: false,
        filteredTree: newFilteredTree,
        totalHits,
        searchResultsMetadata,
        filterIndex: newFilterLoadIndex,
        canLoadMoreFilteredResults
      });
    }
    case REMOVE_ONTOLOGY_FILTER: {
      return Ontology({
        ...state,
        error: defaultState.ontology.error,
        textFilter: defaultState.ontology.textFilter,
        filterValue: defaultState.ontology.filterValue,
        filteredTree: defaultState.ontology.filteredTree,
        searchResultsMetadata: defaultState.searchResultsMetadata,
        totalHits: defaultState.totalHits,
        filterLoadIndex: defaultState.filterIndex,
        canLoadMoreFilteredResults: defaultState.canLoadMoreFilteredResults
      });
    }
    default:
      return state;
  }
};
