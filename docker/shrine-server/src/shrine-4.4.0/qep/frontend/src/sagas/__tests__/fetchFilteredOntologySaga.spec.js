import { put, call, select } from "redux-saga/effects";

import { doFilteredOntologyFetch } from "../fetchFilteredOntologySaga";
import { secureFetch } from "utilities";
import { selectSearchResultsMetadata } from "selectors";

const action = {
  payload: {
    searchString: "mock search",
    filterData: {
      filterType: "none",
      filterValue: "All Concepts"
    }
  }
};
describe("fetchFilteredOntolgoy happy path", () => {
  const generator = doFilteredOntologyFetch(action);

  it("Should select the search results metadata from the store", () => {
    const expected = select(selectSearchResultsMetadata);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should make the call to fetch the filtered ontology.", () => {
    const mockUrl = "/shrine-api/ontology/search";
    const headers = {
      "Content-Type": "application/json"
    };
    const body = {
      searchString: "mock search",
      filterData: { filterType: "none", filterValue: "All Concepts" }
    };
    const expected = call(secureFetch, mockUrl, {
      headers,
      method: "POST",
      body: JSON.stringify(body)
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
  xit("Should dispatch a FETCH_FILTERED_ONTOLOGY_SUCCEEDED action if fetch was successful", () => {
    const action = {
      type: "FETCH_FILTERED_ONTOLOGY_SUCCEEDED",
      payload: "mock response data"
    };
    const expected = put(action);
    const actual = generator.next({ ok: true, data: "mock response data" })
      .value;
    expect(actual).toEqual(expected);
  });
  xit("Close the fetch thread", () => {
    const expected = "fetch of /shrine-api/ontology/search thread closed";
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
});

describe("fetchFilteredOntolgoy fail path", () => {
  const generator = doFilteredOntologyFetch(action);

  it("Should select the search results metadata from the store", () => {
    const expected = select(selectSearchResultsMetadata);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should make the call to fetch the filtered ontology.", () => {
    const mockUrl = "/shrine-api/ontology/search";
    const headers = {
      "Content-Type": "application/json"
    };
    const body = {
      searchString: "mock search",
      filterData: { filterType: "none", filterValue: "All Concepts" }
    };
    const expected = call(secureFetch, mockUrl, {
      headers,
      method: "POST",
      body: JSON.stringify(body)
    });

    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should dispatch a FETCH_FILTERED_ONTOLOGY_FAILED action if fetch failed", () => {
    const action = {
      type: "FETCH_FILTERED_ONTOLOGY_FAILED",
      payload: []
    };
    const expected = put(action);
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
  it("Close the fetch thread", () => {
    const expected = "fetch of /shrine-api/ontology/search thread closed";
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
});
