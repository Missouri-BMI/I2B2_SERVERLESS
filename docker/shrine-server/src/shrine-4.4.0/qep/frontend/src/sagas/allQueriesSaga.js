import { takeLatest, put, select } from "redux-saga/effects";

import { FETCH_ALL_QUERIES, fetchQuerySnapshot } from "../actions";
import { getBaseUrl } from "../utilities";
import { selectLimit, selectSortBy, selectSkip } from "../selectors";

export function* buildQuerySnapshotRequest() {
  const limit = yield select(selectLimit);
  const sortBy = yield select(selectSortBy);
  const skip = yield select(selectSkip);
  const url = `${getBaseUrl()}qep/queryResult?limit=${limit}&sortBy=${sortBy}&skip=${skip}`;
  yield put(fetchQuerySnapshot({ url }));
}

export function* allQueriesSaga() {
  yield takeLatest([FETCH_ALL_QUERIES], buildQuerySnapshotRequest);
}
