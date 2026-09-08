import { call, takeLatest, put } from "redux-saga/effects";

import {
  FETCH_FILTER_OPTIONS,
  fetchFilterOptionsSucceeded,
  fetchFilterOptionsFailed
} from "../actions";
import { getBaseUrl, secureFetch } from "../utilities";

export function* doOntologyFilterOptionsFetch() {
  const url = `${getBaseUrl()}ontology/filterOptions`;
  try {
    const response = yield call(secureFetch, url);
    if (response.ok === true) {
      const filterOptions = response.data;
      yield put(fetchFilterOptionsSucceeded(filterOptions));
    } else {
      yield put(fetchFilterOptionsFailed(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* fetchOntologyFilterOptionsSaga() {
  yield takeLatest(FETCH_FILTER_OPTIONS, doOntologyFilterOptionsFetch);
}
