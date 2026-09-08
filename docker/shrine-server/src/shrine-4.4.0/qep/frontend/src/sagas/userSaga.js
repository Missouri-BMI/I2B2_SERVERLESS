import fetch from "isomorphic-fetch";
import { call, takeLatest, put, apply } from "redux-saga/effects";

import {
  LOGIN_USER,
  CLEAR_LOGIN,
  SET_USER_LOCAL_CONFIG,
  loginUserFailed,
  loginUserSucceeded,
  unexpectedError
} from "actions";
import { getBaseUrl, auth, local, sessionStorage } from "utilities";

export function* doClearLogin() {
  yield call(sessionStorage.clearUserConfig);
  yield call(sessionStorage.clearSessionKey);
  yield call(local.clearLocalKey);
  yield call(auth.clear);
}

export function* doLoginUser(action) {
  const { token } = action.payload;
  const url = `${getBaseUrl()}qep/login`;
  const headers = {
    Authorization: `Basic ${token}`,
    "X-Requested-With": "XMLHttpRequest"
  };

  try {
    const response = yield call(fetch, url, { headers });
    if (response.ok === true) {
      const authData = yield apply(response, response.json);
      auth.token = authData;
      yield put(loginUserSucceeded(authData));
    } else {
      const isAuthenticationFailure = [401, 403].includes(response.status);
      if (isAuthenticationFailure) {
        yield put(loginUserFailed(response));
      } else {
        yield put(unexpectedError(response));
      }
    }
  } finally {
    const msg = `auth thread ${url} thread closed`;
    yield msg;
  }
}

export function* doUpdateUserLocalConfig(action) {
  const { payload: newConfig } = action;

  try {
    const config = local.hasUserConfig()
      ? {
        ...local.getUserConfig(),
        ...newConfig
      }
      : newConfig;
    yield call(local.setUserConfig, config);
  } catch {
    throw Error("there is a problem accessing the user configuration");
  }
}

export function* userSaga() {
  yield takeLatest(LOGIN_USER, doLoginUser);
  yield takeLatest(CLEAR_LOGIN, doClearLogin);
  yield takeLatest(SET_USER_LOCAL_CONFIG, doUpdateUserLocalConfig);
}
