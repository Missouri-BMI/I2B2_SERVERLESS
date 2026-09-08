import { put, call, apply } from "redux-saga/effects";

import { sessionStorage } from "utilities";
import { doCacheViewMode } from "../viewModeSaga";

const store = {};
const mockStorage = {
  setItem: (key, data) => {
    store[key] = data;
  },
  getItem: (key) => store[key],
  removeItem: (key) => {
    delete store[key];
  }
};

sessionStorage.sessionKey = "mock session key";
sessionStorage.storage = mockStorage;

describe("doCacheViewMode", () => {
  it("Should cache the default view mode of QUERY_DEFINITION by default when a user logs in.", () => {
    const action = {
      type: "LOGIN_USER_SUCCEEDED",
      payload: { viewMode: "QUERY_DEFINITION" }
    };
    const generator = doCacheViewMode(action);
    const expected = call(sessionStorage.setUserConfig, action.payload);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should cache the payload if the action is UPDATE_VIEW_MODE", () => {
    const action = {
      type: "UPDATE_VIEW_MODE",
      payload: "QUERY_RESULTS"
    };
    const generator = doCacheViewMode(action);
    const expected = call(sessionStorage.setUserConfig, { viewMode: action.payload });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});
