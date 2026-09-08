import {
  KEEP_ALIVE,
  KEEP_ALIVE_SUCCEEDED,
  KEEP_ALIVE_FAILED,
  LOGIN_USER_SUCCEEDED
} from "actions";
import { defaultState } from "defaultState";
import { Session } from "models";
import { auth } from "utilities";

export const keepAliveReducer = (state = defaultState.session, action) => {
  switch (action.type) {
    case KEEP_ALIVE: {
      return Session({
        ...state,
        active: false
      });
    }

    case KEEP_ALIVE_SUCCEEDED: {
      return Session({
        ...state,
        active: true
      });
    }

    case KEEP_ALIVE_FAILED: {
      return Session({
        ...state,
        active: false
      });
    }

    default: {
      return Session({ ...state });
    }
  }
};
