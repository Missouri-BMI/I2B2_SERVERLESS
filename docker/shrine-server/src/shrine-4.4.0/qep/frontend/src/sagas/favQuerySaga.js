import { call, takeLatest, put } from "redux-saga/effects";
import {FAV_QUERY, startQueryPoll, unexpectedError} from "../actions";
import {getBaseUrl, secureFetchOrLogout} from "../utilities";

export function* doFavQuery(action) {
  const { queryId, faved } = action.payload;
  const favBody = { faved: faved };
  const url = `${getBaseUrl()}qep/changeQueryFav/${queryId}`;
  const fetchConfig = {
    headers: { "Content-Type": "application/json" },
    method: "POST",
    body: JSON.stringify(favBody)
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

export function* favQuerySaga() {
  yield takeLatest(FAV_QUERY, doFavQuery);
}
