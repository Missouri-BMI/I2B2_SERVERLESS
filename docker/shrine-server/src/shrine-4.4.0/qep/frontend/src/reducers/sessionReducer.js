import {
  KEEP_ALIVE,
  KEEP_ALIVE_SUCCEEDED,
  KEEP_ALIVE_FAILED,
  LOGIN_USER,
  LOGIN_USER_SUCCEEDED,
  LOGIN_USER_FAILED,
  CLEAR_LOGIN,
  ACCEPT_TERMS_OF_USE
} from "actions";
import { defaultState } from "defaultState";
import {Session, User, ViewMode, ViewModeTypes} from "models";
import { auth, local, sessionStorage } from "utilities";
import { keepAliveReducer } from "./keepAliveReducer";
import { userReducer } from "./userReducer";

export const sessionReducer = (state = defaultState.session, action) => {
  switch (action.type) {
    case KEEP_ALIVE:
    case KEEP_ALIVE_SUCCEEDED:
    case KEEP_ALIVE_FAILED: {
      return keepAliveReducer(state, action);
    }

    case LOGIN_USER:
    case LOGIN_USER_SUCCEEDED:
    case LOGIN_USER_FAILED:
    case CLEAR_LOGIN: {
      return userReducer(state, action);
    }

    default: {
      const hasLocalKey = local.hasLocalKey();
      const hasSessionKey = sessionStorage.hasSessionKey();

      const active = auth.isAuthenticated;

      if (auth.isAuthenticated) {
        const { username } = auth.tokenAuthorizationObject;
        if (!hasLocalKey) {
          local.localKey = username;
        }

        if (!hasSessionKey) {
          sessionStorage.sessionKey = username;
        }

        const user = User({
          ...state.user,
          isAuthenticated: true,
          username
        });

        return Session({
          ...state,
          user,
          sessionTimeoutMs: auth.timeoutMs,
          active,
        });
      }

      if (hasLocalKey) {
        local.clearLocalKey();
      }

      if (hasSessionKey) {
        sessionStorage.clearSessionKey();
      }

      return Session({
        ...state,
        active: auth.isAuthenticated,
        user: User({ isAuthenticated: false })
      });
    }
  }
};
