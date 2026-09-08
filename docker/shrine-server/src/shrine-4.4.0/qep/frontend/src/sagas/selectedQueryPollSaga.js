import delay from "redux-saga";
import { take, put, takeLatest, fork } from "redux-saga/effects";

import {
  FETCH_QUERY_UPDATE_SUCCEEDED,
  FETCH_QUERY_UPDATE_FAILED,
  START_QUERY_POLL,
  CANCEL_QUERY_POLL,
  UPDATE_VIEW_MODE,
  CLEAR_LOGIN,
  CONTINUE_QUERY_POLL,
  continueQueryPoll,
  fetchQueryTimeout,
  unexpectedError
} from "actions";
import { fetchQueryUpdate } from "../actions";

export function* queryFetchFulfilledSaga() {
  const { payload } = yield take(FETCH_QUERY_UPDATE_SUCCEEDED);
  const { selectedQuery = null, isAllQueriesComplete } = payload;

  if (selectedQuery !== null) {
    const { isComplete, query, dataVersion } = selectedQuery;
    /*
    todo: SHRINE2020-290 change networkId to queryId when server is ready.
  */
    const { networkId: queryId } = query;
    if (isComplete === false || !isAllQueriesComplete) {
      yield put(continueQueryPoll({ queryId, dataVersion }));
    } else {
      yield;
    }
  } else {
    yield;
  }
}

export function* queryFetchRejectedSaga() {
  const { payload } = yield take(FETCH_QUERY_UPDATE_FAILED);
  const { queryId, response } = payload;
  const { status, statusText, url } = response;

  if (status === 429 || status === 503) {
    yield put(
      fetchQueryTimeout({
        status,
        statusText,
        url,
        queryId
      })
    );
    yield delay(15000);
    yield put(continueQueryPoll({ queryId }));
  } else {
    yield put(unexpectedError(response));
  }
}

export function* queryPollSaga(action) {
  const {
    queryId,
    dataVersion = -1,
    queryName,
    queryAsHtmlString,
    changeDate,
    demographicDistribution
  } = action.payload;
  try {
    yield fork(queryFetchRejectedSaga);
    yield fork(queryFetchFulfilledSaga);
    yield put(fetchQueryUpdate({ queryId, dataVersion, queryName, queryAsHtmlString, changeDate, demographicDistribution}));
  } finally {
    console.log(
      `Polling thread for queryId: ${queryId} ${dataVersion} cancelled`
    );
  }
}

export function* startQueryPollSaga() {
  while (true) {
    const queryPollThread = yield takeLatest(
      [START_QUERY_POLL, CONTINUE_QUERY_POLL],
      queryPollSaga
    );

    yield take([CANCEL_QUERY_POLL, CLEAR_LOGIN, UPDATE_VIEW_MODE]);
    if (queryPollThread.isRunning()) {
      yield queryPollThread.cancel();
    }
  }
}
