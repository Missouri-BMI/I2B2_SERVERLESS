import { call, takeLatest } from "redux-saga/effects";

import { ACCEPT_TERMS_OF_USE } from "actions";
import { local} from "utilities";

export function* doCacheTermsOfUseAccepted(action) {
  const {
    termsOfUseText = null,
  } = action.payload;

  try {
    yield call(local.setTOUAccepted, termsOfUseText);
  } catch {
    throw Error("There is a problem accessing the user configuration for terms of use.");
  }
}

export function* termsOfUseSaga() {
  yield takeLatest([ACCEPT_TERMS_OF_USE], doCacheTermsOfUseAccepted);
}
