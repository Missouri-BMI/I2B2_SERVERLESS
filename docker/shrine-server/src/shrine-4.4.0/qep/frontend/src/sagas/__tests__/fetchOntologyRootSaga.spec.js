import { put, call, apply } from "redux-saga/effects";
import { doOntologyRootFetch } from "../fetchOntologyRootSaga";
import { secureFetch } from "../../utilities";

describe("fetchOntologyRootSaga happy path", () => {
  const generator = doOntologyRootFetch();

  it("Should make the call to fetch the root ontology.", () => {
    const mockUrl = "/shrine-api/ontology/root";
    const expected = call(secureFetch, mockUrl);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
  it("Should dispatch a FETCH_ONTOLOGY_ROOT_SUCCEEDED action if fetch was successful", () => {
    const action = {
      type: "FETCH_ONTOLOGY_ROOT_SUCCEEDED",
      payload: "mock data"
    };
    const expected = put(action);
    const actual = generator.next({ ok: true, data: "mock data" }).value;
    expect(actual).toEqual(expected);
  });
  it("Close the fetch thread", () => {
    const expected = "fetch of /shrine-api/ontology/root thread closed";
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
});

describe("fetchOntologyRootSaga fail path", () => {
  const generator = doOntologyRootFetch();

  it("Should make the call to fetch the root ontology.", () => {
    const mockUrl = "/shrine-api/ontology/root";
    const expected = call(secureFetch, mockUrl);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should dispatch a FETCH_ONTOLOGY_ROOT_FAILED action if fetch failed", () => {
    const action = {
      type: "FETCH_ONTOLOGY_ROOT_FAILED",
      payload: []
    };
    const expected = put(action);
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
  it("Close the fetch thread", () => {
    const expected = "fetch of /shrine-api/ontology/root thread closed";
    const actual = generator.next([]).value;
    expect(actual).toEqual(expected);
  });
});
