import { call, takeLatest, put } from "redux-saga/effects";

import {
  FETCH_NETWORK_CONFIG,
  fetchNetworkConfigSucceeded,
  unexpectedError
} from "../actions";
import { getBaseUrl, secureFetch } from "../utilities";

export function* doNetworkConfigFetch() {
  const configUrl = `${getBaseUrl()}staticData/webClientConfig`;
  try {
    const response = yield call(secureFetch, configUrl);
    if (response.ok === true) {
      yield put(fetchNetworkConfigSucceeded(response.data));
    } else {
      yield put(unexpectedError(response));
    }
  } finally {
    const msg = `fetch of ${configUrl} thread closed`;
    yield msg;
  }
}

export function* fetchNetworkConfigSaga() {
  yield takeLatest(FETCH_NETWORK_CONFIG, doNetworkConfigFetch);
}
