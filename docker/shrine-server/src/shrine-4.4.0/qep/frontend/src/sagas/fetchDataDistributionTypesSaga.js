import { call, takeLatest, put } from "redux-saga/effects";

import {
  FETCH_DATA_DISTRIBUTION_TYPES,
  fetchDataDistributionTypesSucceeded,
  unexpectedError
} from "../actions";
import { getBaseUrl, secureFetch } from "../utilities";

export function* doDataDistributionFetch() {
  const configUrl = `${getBaseUrl()}staticData/dataDistributionTypes`;
  try {
    const response = yield call(secureFetch, configUrl);
    if (response.ok === true) {
      yield put(fetchDataDistributionTypesSucceeded(response.data));
    } else {
      yield put(unexpectedError(response));
    }
  } finally {
    const msg = `fetch of ${configUrl} thread closed`;
    yield msg;
  }
}

export function* fetchDataDistributionTypesSaga() {
  yield takeLatest(FETCH_DATA_DISTRIBUTION_TYPES, doDataDistributionFetch);
}
