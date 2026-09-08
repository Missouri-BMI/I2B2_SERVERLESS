import {call, put, takeLatest} from 'redux-saga/effects';
import {EXPORT_DEMOGRAPHIC_TO_CSV, fetchDemographicDataFailed, fetchDemographicDataSucceeded} from '../actions';
import { getBaseUrl, secureBlobFetchOrLogout} from '../utilities';

export function* doExportDemographicToCSV(action) {

  const queryId = action.payload;

  const url = `${getBaseUrl()}qep/demographic/csv?queryId=${queryId}`;
  const fetchConfig = {
    method: "POST",
  };

  try {
    const response = yield call(secureBlobFetchOrLogout, url, fetchConfig);
    if (response.ok === true) {
      const queryData = URL.createObjectURL(response.blob);

      yield put(fetchDemographicDataSucceeded(queryData));
    } else {
      yield put(fetchDemographicDataFailed(response));
    }
  } finally {
    const msg = `fetch of ${url} thread closed`;
    yield msg;
  }
}

export function* demographicExportSaga() {
  yield takeLatest(EXPORT_DEMOGRAPHIC_TO_CSV, doExportDemographicToCSV);
}
