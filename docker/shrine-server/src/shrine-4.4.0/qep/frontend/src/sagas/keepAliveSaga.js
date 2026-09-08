import { call, takeLatest, put } from "redux-saga/effects";
import { KEEP_ALIVE, keepAliveFailed, keepAliveSucceeded } from "../actions";
import {getBaseUrl, secureFetchOrLogout} from "../utilities";

export function* doKeepAlive() {
  const url = `${getBaseUrl()}qep/keepAlive`;
  const fetchConfig = {
    method: "GET"
  };

  try {
    const response = yield call(secureFetchOrLogout, url, fetchConfig);
    if (response.ok) {
      yield put(keepAliveSucceeded());
    } else {
      yield put(keepAliveFailed());
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* keepAliveSaga() {
  yield takeLatest(KEEP_ALIVE, doKeepAlive);
}
