import { takeLatest, select, put } from "redux-saga/effects";

import { fetchQuerySnapshot, FETCH_QUERY_UPDATE } from "../actions";
import { getBaseUrl } from "../utilities";
import {
  selectLimit,
  selectSortBy,
  selectSortSiteBy,
  selectSkip,
} from "../selectors";

export function* buildQuerySnapshotRequest(action) {
  const { queryId, dataVersion } = action.payload;
  const limit = yield select(selectLimit);
  const sortBy = yield select(selectSortBy);
  const sortSiteBy = yield select(selectSortSiteBy);
  const skip = yield select(selectSkip);

  let timeoutSeconds = "";
  if (dataVersion === -1) {
    timeoutSeconds = "&timeoutSeconds=0";
  }
  const url = `${getBaseUrl()}qep/queryResult?networkId=${queryId}&limit=${limit}&sortBy=${sortBy}&skip=${skip}&afterVersion=${dataVersion}&sortSiteBy=${sortSiteBy}${timeoutSeconds}`;
  yield put(
    fetchQuerySnapshot({
      url,
      isQueryUpdateRequest: true,
      queryId,
    })
  );
}

export function* queryUpdateSaga() {
  yield takeLatest(FETCH_QUERY_UPDATE, buildQuerySnapshotRequest);
}
