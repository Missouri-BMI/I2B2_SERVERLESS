import {
  fetchAllQueries,
  fetchAllQueriesFailed,
  fetchAllQueriesSucceeded,
  fetchOntologyRoot,
  fetchOntologyRootFailed,
  fetchOntologyRootSucceeded
} from "..";

describe("Fetch All Queries actions", () => {
  const payload = "mock paylod";
  it("fetchAllQueries Should be properly formatted", () => {
    const expected = {
      type: "FETCH_ALL_QUERIES",
      payload
    };
    const actual = fetchAllQueries(payload);
    expect(actual).toEqual(expected);
  });
  it("fetchAllQueriesFailed Should be properly formatted", () => {
    const expected = {
      type: "FETCH_ALL_QUERIES_FAILED",
      payload
    };
    const actual = fetchAllQueriesFailed(payload);
    expect(actual).toEqual(expected);
  });
  it("fetchAllQueriesFailed Should be properly formatted", () => {
    const expected = {
      type: "FETCH_ALL_QUERIES_SUCCEEDED",
      payload
    };
    const actual = fetchAllQueriesSucceeded(payload);
    expect(actual).toEqual(expected);
  });
  it("fetch ontology Should be properly formatted", () => {
    const expected = {
      type: "FETCH_ONTOLOGY_ROOT",
      payload
    };
    const actual = fetchOntologyRoot(payload);
    expect(actual).toEqual(expected);
  });
  it("fetch ontology failed Should be properly formatted", () => {
    const expected = {
      type: "FETCH_ONTOLOGY_ROOT_FAILED",
      payload
    };
    const actual = fetchOntologyRootFailed(payload);
    expect(actual).toEqual(expected);
  });
  it("fetch ontology succeeded Should be properly formatted", () => {
    const expected = {
      type: "FETCH_ONTOLOGY_ROOT_SUCCEEDED",
      payload
    };
    const actual = fetchOntologyRootSucceeded(payload);
    expect(actual).toEqual(expected);
  });
});
