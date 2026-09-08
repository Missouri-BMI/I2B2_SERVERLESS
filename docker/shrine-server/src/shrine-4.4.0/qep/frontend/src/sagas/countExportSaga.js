import {call, put, takeLatest} from 'redux-saga/effects';
import {EXPORT_COUNT_TO_CSV, fetchCountCsvDataFailed, fetchCountCsvDataSucceeded} from '../actions';
import { getBaseUrl, secureBlobFetchOrLogout} from '../utilities';

export function* doExportCountToCSV(action) {

  const queryId = action.payload;

  const url = `${getBaseUrl()}qep/count/csv?queryId=${queryId}`;
  const fetchConfig = {
    method: "POST",
  };

  try {
    const response = yield call(secureBlobFetchOrLogout, url, fetchConfig);
    if (response.ok === true) {
      const queryData = URL.createObjectURL(response.blob);

      yield put(fetchCountCsvDataSucceeded(queryData));
    } else {
      yield put(fetchCountCsvDataFailed({ response }));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* countExportSaga() {
  yield takeLatest(EXPORT_COUNT_TO_CSV, doExportCountToCSV);
}
