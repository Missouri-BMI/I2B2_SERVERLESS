import { call, takeLatest, put, select } from "redux-saga/effects";

import {
  FETCH_FILTERED_ONTOLOGY,
  fetchFilteredOntologyFailed,
  fetchFilteredOntologySucceeded
} from "actions";
import { selectSearchResultsMetadata } from "selectors";
import { getBaseUrl, secureFetch } from "utilities";

export function* doFilteredOntologyFetch(action) {
  const searchResultsMetadata = yield select(selectSearchResultsMetadata);
  // Destructing here to ensure shape of payload
  const {
    searchString,
    filterData: { filterType, filterValue }
  } = action.payload;
  const url = `${getBaseUrl()}ontology/search`;
  const headers = {
    "Content-Type": "application/json"
  };
  const body = JSON.stringify(
    searchResultsMetadata
      ? {
          searchString,
          filterData: { filterType, filterValue },
          previousSearchMetadata: searchResultsMetadata
        }
      : { searchString, filterData: { filterType, filterValue } }
  );
  const fetchConfig = {
    headers,
    method: "POST",
    body
  };

  try {
    const response = yield call(secureFetch, url, fetchConfig);
    if (response.ok) {
      const filteredTree = response.data;
      yield put(fetchFilteredOntologySucceeded(filteredTree));
    } else {
      yield put(fetchFilteredOntologyFailed(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* fetchFilteredOntologySaga() {
  yield takeLatest(FETCH_FILTERED_ONTOLOGY, doFilteredOntologyFetch);
}
