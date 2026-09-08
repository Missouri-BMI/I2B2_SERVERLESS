import { call, takeLatest, put, select, apply, race, cancelled } from "redux-saga/effects";

import {
  CLEAR_LOGIN,
  FETCH_QUERY_SNAPSHOT,
  fetchQueryUpdateFailed,
  fetchQueryUpdateSucceeded,
  fetchAllQueriesSucceeded,
  fetchAllQueriesFailed,
} from "../actions";
import { secureFetchOrLogout } from "../utilities";
import {take} from "redux-saga/dist/redux-saga-effects-npm-proxy.cjs";

export function* doFetchQuerySnapshot(action) {
  const {
    url,
    isQueryUpdateRequest = false,
    queryId = "not provided",
  } = action.payload;

  try {

    const response = yield call(secureFetchOrLogout, url);

    if (response.ok === true) {
      const queryData = response.data;

      console.log("Finished fetching querydata");
      console.log("queryData: ", queryData);
      if (isQueryUpdateRequest) {
        yield put(fetchQueryUpdateSucceeded(queryData));
      }
      yield put(fetchAllQueriesSucceeded(queryData));
    } else {
      if (isQueryUpdateRequest) {
        yield put(
          fetchQueryUpdateFailed({
            response,
            queryId,
          })
        );
      }
      yield put(fetchAllQueriesFailed(response));
    }
  } finally {

    if (yield cancelled()) {
      console.log(`cancelled query snapshot`);
    }else {
      yield `fetch of ${url} completed`;
    }
  }
}

export function* fetchQuerySnapshotSaga() {
  //yield takeLatest([FETCH_QUERY_SNAPSHOT], doFetchQuerySnapshot);

  yield takeLatest([FETCH_QUERY_SNAPSHOT], function* (...args) {
    yield race({
      task: call(doFetchQuerySnapshot, ...args),
      cancel: take(CLEAR_LOGIN)
    })
  })
}
