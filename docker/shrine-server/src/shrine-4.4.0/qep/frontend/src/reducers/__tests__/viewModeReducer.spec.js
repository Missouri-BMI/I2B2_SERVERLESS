import { ViewModeTypes, ViewMode } from "models";
import { defaultState } from "defaultState";
import { sessionStorage } from "utilities";
import { viewModeReducer } from "../viewModeReducer";

describe("viewModeReducer", () => {
  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };
    const expected = defaultState.viewMode;
    const actual = viewModeReducer(undefined, action);
    expect(actual).toEqual(expected);
  });

  it("The view mode should be pulled from the session storage if it has a value.", () => {
    const store = {};
    sessionStorage.storage = {
      setItem: (key, data) => {
        store[key] = data;
      },
      getItem: (key) => store[key],
      removeItem: (key) => {
        delete store[key];
      }
    };
    sessionStorage.sessionKey = "mock session key";
    sessionStorage.setUserConfig({ viewMode: ViewModeTypes.QUERY_RESULTS });
    const action = { type: "UKNOWN_ACTION" };
    const expected = ViewMode({ type: ViewModeTypes.QUERY_RESULTS });
    const actual = viewModeReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
  it("Should set the viewMode to QUERY_DEFINITION when the user logs in", () => {
    const action = { type: "LOGIN_USER_SUCCEEDED" };
    const expected = ViewMode({
      type: ViewModeTypes.QUERY_DEFINITION,
      justLoggedIn: true
    });
    const actual = viewModeReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
  it("Should set the viewMode to LOGIN on when the user logs out.", () => {
    const action = { type: "CLEAR_LOGIN" };
    const expected = ViewMode({ type: "LOGIN" });
    const actual = viewModeReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
  it("Should update the view mode to the one provied", () => {
    const action = {
      type: "UPDATE_VIEW_MODE",
      payload: ViewModeTypes.QUERY_RESULTS
    };
    const expected = ViewMode({ type: action.payload });
    const actual = viewModeReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
});
