import { put, call } from "redux-saga/effects";

import { doNetworkConfigFetch } from "../fetchNetworkConfigSaga";
import { secureFetch } from "utilities";
import { selectSearchResultsMetadata } from "selectors";

const action = {
  payload: {
    domain: "shrine-webclient-dev-node01.catalyst.harvard.edu",
    name: "SHRINE",
    shrineUrl: null,
    siteAdminEmail: "isha_test@goo.com",
    termsOfUseText: "isha_terms-of-use text",
    unauthorizedMessage: "not authorized per Isha",
    usernameLabel: "isha_test",
    passwordLabel: "isha_test",
    defaultNumberOfOntologyChildren: 10000,
    queryFavingInstructions: "isha_test",
    favingIconInstructions: "isha's test faving",
    favPlaceholderText: "isha's sample message",
    networkHelpUrl: "http://mock-network-help/"
  }
};
describe("fetchNetworkConfig happy path", () => {
  const generator = doNetworkConfigFetch(action);

  it("Should make the call to fetch the networkConfig.", () => {
    const mockUrl = "/shrine-api/staticData/webClientConfig";

    const expected = call(secureFetch, mockUrl);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
  it("Should dispatch a FETCH_NETWORK_CONFIG_SUCCEEDED action if fetch was successful", () => {
    const action = {
      type: "FETCH_NETWORK_CONFIG_SUCCEEDED",
      payload: "mock response data"
    };
    const expected = put(action);
    const actual = generator.next({ ok: true, data: "mock response data" })
      .value;
    expect(actual).toEqual(expected);
  });
  it("Close the fetch thread", () => {
    const expected =
      "fetch of /shrine-api/staticData/webClientConfig thread closed";
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
});

describe("fetchNetworkConfig fail path", () => {
  const generator = doNetworkConfigFetch(action);

  it("Should make the call to fetch the network config", () => {
    const mockUrl = "/shrine-api/staticData/webClientConfig";
    const expected = call(secureFetch, mockUrl);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should dispatch a UNEXPECTED_ERROR action if fetch failed", () => {
    const action = {
      type: "UNEXPECTED_ERROR",
      payload: []
    };
    const expected = put(action);
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
  it("Close the fetch thread", () => {
    const expected =
      "fetch of /shrine-api/staticData/webClientConfig thread closed";
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
});
