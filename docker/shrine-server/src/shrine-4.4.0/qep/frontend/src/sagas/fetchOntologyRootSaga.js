import { call, takeLatest, put } from "redux-saga/effects";

import {
  FETCH_ONTOLOGY_ROOT,
  fetchOntologyRootFailed,
  fetchOntologyRootSucceeded
} from "../actions";
import { getBaseUrl, secureFetch } from "../utilities";

export function* doOntologyRootFetch() {
  const url = `${getBaseUrl()}ontology/root`;
  try {
    const response = yield call(secureFetch, url);
    if (response.ok === true) {
      const rootOntology = response.data;
      yield put(fetchOntologyRootSucceeded(rootOntology));
    } else {
      yield put(fetchOntologyRootFailed(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* fetchOntologyRootSaga() {
  yield takeLatest(FETCH_ONTOLOGY_ROOT, doOntologyRootFetch);
}
