import {
  LOGIN_USER,
  LOGIN_USER_SUCCEEDED,
  LOGIN_USER_FAILED,
  CLEAR_LOGIN,
  CLEAR_WARNING
} from "actions";
import { defaultState } from "defaultState";
import { User, Error, Session } from "models";
import { auth, local, sessionStorage } from "utilities";

export const userReducer = (state = defaultState.session, action) => {
  switch (action.type) {
    case CLEAR_WARNING: {
      const error = Error({
        hasError: false,
      });
      return User({
        ...state,
        isAuthenticated: false,
        isAuthenticating: false,
        error
      });
    }
    case CLEAR_LOGIN: {
      const user = { ...defaultState.session.user, wasLoggedIn: true };
      return Session({
        user
      });
    }
    case LOGIN_USER: {
      const { username } = action.payload;
      const user = User({
        ...state.user,
        username,
        loginAttempts: state.user.loginAttempts + 1,
        isAuthenticating: true,
        isAuthenticated: false,
        wasLoggedIn: false
      });

      return Session({
        ...state,
        user
      });
    }
    case LOGIN_USER_SUCCEEDED: {
      const { sessionTimeoutMs, sessionId } = action.payload;
      const user = User({
        ...state.user,
        isAuthenticating: false,
        isAuthenticated: true
      });

      return Session({
        ...state,
        active: true,
        sessionTimeoutMs,
        sessionId,
        user
      });
    }
    case LOGIN_USER_FAILED: {
      const { status, statusText, url } = action.payload;
      const message =
        status === 401 || status === 403
          ? "username or password is invalid "
          : `${status} ${statusText}`;
      const error = Error({
        hasError: true,
        message,
        url
      });
      const user = User({
        ...state.user,
        isAuthenticated: false,
        isAuthenticating: false,
        error
      });
      return Session({
        ...state,
        user
      });
    }
    default: {
      const hasLocalKey = local.hasLocalKey();
      const hasSessionKey = sessionStorage.hasSessionKey();

      if (auth.isAuthenticated) {
        const { username } = auth.tokenAuthorizationObject;
        if (!hasLocalKey) {
          local.localKey = username;
        }

        if (!hasSessionKey) {
          sessionStorage.sessionKey = username;
        }
        return User({
          ...state,
          isAuthenticated: true,
          username,
          sessionTimeoutMs: auth.timeoutMs
        });
      }

      if (hasLocalKey) {
        local.clearLocalKey();
      }

      if (hasSessionKey) {
        sessionStorage.clearSessionKey();
      }
      const user = User({
        isAuthenticated: false
      });
      return Session({ ...state, user });
    }
  }
};
