import {call, put} from "redux-saga/dist/redux-saga-effects-npm-proxy.cjs";
import {clearLogin} from "../actions";
import { secureFetch, secureBlobFetch } from "utilities";

export function* secureFetchOrLogout (url, config){
  const response = yield call(secureFetch, url, config);
  const isAuthenticationFailure = [401, 403].includes(response.status);
  if (isAuthenticationFailure) {
      yield put(clearLogin());
  }
  else {
    return response;
  }
}

export function* secureBlobFetchOrLogout (url, config) {
  const response = yield call(secureBlobFetch, url, config);

  const isAuthenticationFailure = [401, 403].includes(response.status);
  if (isAuthenticationFailure) {
    yield put(clearLogin());
  }

  return response;
}


