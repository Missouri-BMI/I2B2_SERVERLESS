import { Error, User, Session } from "models";
import { defaultState } from "defaultState";
import { userReducer } from "../userReducer";

describe("userReducer", () => {
  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };
    const expected = defaultState.session.user;
    const { user: actual } = userReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
  it("Should reset the user to the default on CLEAR_LOGIN", () => {
    const action = { type: "CLEAR_LOGIN" };
    const expected = { ...defaultState.session.user, wasLoggedIn: true };
    const { user: actual } = userReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
  it("Should update the username, isAuthenticating and loginAttempts on USER_LOGIN ", () => {
    const action = {
      type: "LOGIN_USER",
      payload: { username: "mockusername" }
    };
    const expected = {
      ...defaultState.session.user,
      isAuthenticating: true,
      loginAttempts: 1,
      username: "mockusername"
    };
    const { user: actual } = userReducer(
      { user: { loginAttempts: 0 } },
      action
    );
    expect(actual).toEqual(expected);
  });

  it("Should update the isAuthenticated member on USER_LOGIN_SUCCEEDED", () => {
    const action = {
      type: "LOGIN_USER_SUCCEEDED",
      payload: {
        sessionTimeoutMs: 1800000,
        sessionId: 0
      }
    };
    const expected = Session({
      user: User({
        ...defaultState.session.user,
        isAuthenticated: true
      }),
      active: true,
      sessionTimeoutMs: 1800000
    });
    const actual = userReducer(defaultState.user, action);
    expect(actual).toEqual(expected);
  });

  it("Should update the error data if LOGIN_USER_FAILED", () => {
    const action = {
      type: "LOGIN_USER_FAILED",
      payload: {
        status: "mock failure status",
        url: "mockurl",
        statusText: "mock status text"
      }
    };
    const expected = {
      ...defaultState.session.user,
      error: Error({
        hasError: true,
        message: "mock failure status mock status text",
        url: "mockurl"
      })
    };
    const { user: actual } = userReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
});
