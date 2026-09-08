import { call, takeLatest, put } from "redux-saga/effects";

import {
  FETCH_ONTOLOGY_SEARCH_SUGGESTIONS,
  fetchOntologySearchSuggestionsSucceeded
} from "actions";
import { getBaseUrl, secureFetch } from "../utilities";
import { unexpectedError } from "../actions";

export function* doAutoSuggestionsFetch(action) {
  const { suggestString } = action.payload;
  const url = `${getBaseUrl()}ontology/suggest`;
  const headers = {
    "Content-Type": "application/json"
  };
  const fetchConfig = {
    headers,
    method: "POST",
    body: JSON.stringify({ suggestString })
  };
  try {
    const response = yield call(secureFetch, url, fetchConfig);
    if (response.ok === true) {
      const autoSuggestData = response.data;
      yield put(fetchOntologySearchSuggestionsSucceeded(autoSuggestData));
    } else {
      yield put(unexpectedError(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* autoSuggestSaga() {
  yield takeLatest(FETCH_ONTOLOGY_SEARCH_SUGGESTIONS, doAutoSuggestionsFetch);
}
