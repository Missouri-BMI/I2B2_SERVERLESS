import { put, call, apply } from "redux-saga/effects";
import fetch from "isomorphic-fetch";

import { auth } from "utilities";
import { doLoginUser,  doUpdateUserLocalConfig } from "../userSaga";
import { local, sessionStorage } from "../../utilities";

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

local.localKey = "mock session key";
sessionStorage.sessionKey = "mock session key";

describe("doLoginUser Login Success", () => {
  auth.storage = mockStorage;

  const action = {
    type: "LOGIN_USER",
    payload: {
      token: btoa("mocktoken"),
      username: "mockusername"
    }
  };
  const generator = doLoginUser(action);

  it("Should make a call to log in the user", () => {
    const expected = call(fetch, "/shrine-api/qep/login", {
      headers: {
        Authorization: `Basic ${btoa("mocktoken")}`,
        "X-Requested-With": "XMLHttpRequest"
      }
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should parse a successful login request", () => {
    const response = {
      ok: true,
      json: () => "response data"
    };
    const expected = apply(response, response.json);
    const actual = generator.next(response).value;
    expect(actual).toEqual(expected);
  });

  it("should dispatch a login success action", () => {
    const expected = put({
      type: "LOGIN_USER_SUCCEEDED",
      payload: { sessionTimeoutMs: 123 }
    });

    const actual = generator.next({ sessionTimeoutMs: 123 }).value;
    expect(actual).toEqual(expected);
  });

  it("Should close the fetch thread", () => {
    const expected = "auth thread /shrine-api/qep/login thread closed";
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});

describe("doLoginUser Login Failure", () => {
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
  auth.storage = mockStorage;

  const action = {
    type: "LOGIN_USER",
    payload: {
      token: btoa("mocktoken"),
      username: "mockusername"
    }
  };
  const generator = doLoginUser(action);

  it("Should make a call to log in the user", () => {
    const expected = call(fetch, "/shrine-api/qep/login", {
      headers: {
        Authorization: `Basic ${btoa("mocktoken")}`,
        "X-Requested-With": "XMLHttpRequest"
      }
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("should dispatch a login failed action", () => {
    const response = {
      ok: false,
      status: 401
    };
    const expected = put({
      type: "LOGIN_USER_FAILED",
      payload: { ok: false, status: 401 }
    });
    const actual = generator.next(response).value;
    console.log(actual);
    expect(actual).toEqual(expected);
  });

  it("Should close the fetch thread", () => {
    const expected = "auth thread /shrine-api/qep/login thread closed";
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});

describe("doLoginUser Unknown Failure", () => {
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
  auth.storage = mockStorage;

  const action = {
    type: "LOGIN_USER",
    payload: {
      token: btoa("mocktoken"),
      username: "mockusername"
    }
  };
  const generator = doLoginUser(action);

  it("Should make a call to log in the user", () => {
    const expected = call(fetch, "/shrine-api/qep/login", {
      headers: {
        Authorization: `Basic ${btoa("mocktoken")}`,
        "X-Requested-With": "XMLHttpRequest"
      }
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("should dispatch a unexpected error action", () => {
    const response = {
      ok: false,
      status: 500
    };
    const expected = put({
      type: "UNEXPECTED_ERROR",
      payload: { ok: false, status: 500 }
    });
    const actual = generator.next(response).value;
    console.log(actual);
    expect(actual).toEqual(expected);
  });

  it("Should close the fetch thread", () => {
    const expected = "auth thread /shrine-api/qep/login thread closed";
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});

describe("doUpdateLocalUserConfig", () => {
  local.storage = mockStorage;
  const action = {
    type: "SET_USER_LOCAL_CONFIG",
    payload: { mockConfigItem: "mock config item" }
  };
  const generator = doUpdateUserLocalConfig(action);

  it("Should set the user local config", () => {
    const expected = call(local.setUserConfig, action.payload);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});
