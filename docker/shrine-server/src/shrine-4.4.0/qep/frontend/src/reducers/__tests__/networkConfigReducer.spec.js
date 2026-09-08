import { Error, NetworkConfig } from "../../models";
import { defaultState } from "defaultState";
import { networkConfigReducer } from "../networkConfigReducer";

describe("networkConfigReducer", () => {
  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };
    const expected = defaultState.networkConfig;
    const actual = networkConfigReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
  it("Should update the isFetching element to true if FETCH_NETWORK_CONFIG is dispatched ", () => {
    const action = {
      type: "FETCH_NETWORK_CONFIG",
      payload: {}
    };
    const expected = NetworkConfig({
      isFetching: true
    });
    const actual = networkConfigReducer(undefined, action);
    expect(actual).toEqual(expected);
  });

  it("Should update the config info and it should set isFetching to false if FETCH_NETWORK_CONFIG_SUCCEEDED", () => {
    const action = {
      type: "FETCH_NETWORK_CONFIG_SUCCEEDED",
      payload: NetworkConfig({
        isFetching: true,
        siteAdminEmail: "test@test.com",
        termsOfUseText: "terms-of-use text"
      })
    };
    const expected = NetworkConfig({
      isAuthenticated: true,
      siteAdminEmail: "test@test.com",
      termsOfUseText: "terms-of-use text"
    });
    const actual = networkConfigReducer(undefined, action);
    expect(actual).toEqual(expected);
  });

  it("Should update the error data if FETCH_NETWORK_CONFIG_FAILED", () => {
    const action = {
      type: "FETCH_NETWORK_CONFIG_FAILED",
      payload: {
        status: "mock failure status",
        url: "mockurl",
        statusText: "mock status text"
      }
    };
    const expected = NetworkConfig({
      error: Error({
        hasError: true,
        message: "mock failure status mock status text",
        url: "mockurl"
      })
    });
    const actual = networkConfigReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
});
