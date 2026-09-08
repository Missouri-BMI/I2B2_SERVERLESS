import { call, takeLatest, put } from "redux-saga/effects";
import {RENAME_QUERY, startQueryPoll} from "../actions";
import {getBaseUrl, secureFetchOrLogout} from "../utilities";
import {
  fetchAllQueries,
  fetchQueryUpdateFailed,
  unexpectedError
} from "../actions";

export function* doRenameQuery(action) {
  const { queryId, name, notes } = action.payload;
  const renameQueryBody = { name, notes };

  const url = `${getBaseUrl()}qep/changeQueryNameAndNotes/${queryId}`;
  const fetchConfig = {
    headers: { "Content-Type": "application/json" },
    method: "POST",
    body: JSON.stringify(renameQueryBody)
  };

  try {
    const response = yield call(secureFetchOrLogout, url, fetchConfig);
    if (response.ok === true) {
      yield put(startQueryPoll({ queryId }));
    } else {
      yield put(unexpectedError(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* renameQuerySaga() {
  yield takeLatest(RENAME_QUERY, doRenameQuery);
}
