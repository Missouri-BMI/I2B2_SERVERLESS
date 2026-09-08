import { call, takeLatest } from "redux-saga/effects";

import { ViewModeTypes, isValidViewMode } from "models";
import { UPDATE_VIEW_MODE, LOGIN_USER_SUCCEEDED } from "actions";
import { sessionStorage } from "utilities";

export function* doCacheViewMode(action) {
  const defaultViewModeType = ViewModeTypes.QUERY_DEFINITION;
  const { payload } = action;
  const wasViewModeUpdated = action.type === UPDATE_VIEW_MODE;

  try {
    const config = sessionStorage.hasUserConfig() ? sessionStorage.getUserConfig() : {};
    const viewMode = wasViewModeUpdated ? payload : defaultViewModeType;
    yield call(sessionStorage.setUserConfig, { ...config, viewMode });
  } catch {
    throw Error("there is a problem accessing the user configuration");
  }
}

export function* viewModeSaga() {
  yield takeLatest([UPDATE_VIEW_MODE, LOGIN_USER_SUCCEEDED], doCacheViewMode);
}
