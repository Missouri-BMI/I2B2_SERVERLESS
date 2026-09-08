import { call, takeLatest, put } from "redux-saga/effects";
import {
  RELOAD_QUERY,
  reloadQuerySucceeded,
  unexpectedError,
  updateViewMode
} from "../actions";
import {getBaseUrl, secureFetchOrLogout} from "../utilities";
import { ViewModeTypes } from "../models";

export function* doReloadQuery(action) {
  const { queryId } = action.payload;

  const url = `${getBaseUrl()}qep/query/${queryId}`;
  const fetchConfig = {
    method: "GET"
  };

  try {
    const response = yield call(secureFetchOrLogout, url, fetchConfig);
    if (response.ok === true) {
      const queryDefinition = response.data;
      yield put(updateViewMode(ViewModeTypes.QUERY_DEFINITION));
      yield put(reloadQuerySucceeded({ ...queryDefinition, queryId }));
    } else {
      yield put(unexpectedError(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* reloadQuerySaga() {
  yield takeLatest(RELOAD_QUERY, doReloadQuery);
}
