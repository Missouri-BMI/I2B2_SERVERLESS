import { Ontology } from "../../models";
import { ontologyReducer } from "../ontologyReducer";

describe("ontologyReducer", () => {
  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };
    const expected = Ontology();
    const actual = ontologyReducer(undefined, action);

    // default state values.
    expect(actual).toEqual(expected);
  });

  it("FETCH_ONTOLOGY_ROOT, should set the isFetching property to true", () => {
    const action = {
      type: "FETCH_ONTOLOGY_ROOT"
    };
    const expected = Ontology({ isFetching: true });
    const actual = ontologyReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_ONTOLOGY_ROOT_FAILED, should provide error information", () => {
    const action = {
      type: "FETCH_ONTOLOGY_ROOT_FAILED",
      payload: {
        status: "mock error status",
        statusText: "mock error status text",
        url: "mock error url"
      }
    };
    const { status, statusText, url } = action.payload;
    const expected = Ontology({
      isFetching: false,
      error: {
        hasError: true,
        message: `${status} ${statusText}`,
        url
      },
      root: [],
      terms: new Map(),
      filterOptions: null,
      filteredTree: null,
      textFilter: null,
      filterValue: null
    });
    const actual = ontologyReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_ONTOLOGY_ROOT_SUCCEEDED, should update the ontology root", () => {
    const action = {
      type: "FETCH_ONTOLOGY_ROOT_SUCCEEDED",
      payload: ["item1", "item2", "item3"]
    };
    const expected = {
      ...Ontology({
        root: [...action.payload]
      }),
      isFetching: false
    };
    const actual = ontologyReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_FILTERED_ONTOLOGY:, should set the search filter property", () => {
    const action = {
      type: "FETCH_FILTERED_ONTOLOGY",
      payload: {
        searchString: "mock search",
        filterData: {
          filterValue: "mock filter value",
          filterType: "mock filter type"
        }
      }
    };
    const expected = Ontology({
      isFetching: true,
      error: {
        hasError: false,
        message: null,
        url: null
      },
      root: [],
      terms: new Map(),
      filterOptions: null,
      filteredTree: null,
      textFilter: "mock search",
      filterValue: "mock filter value"
    });
    const actual = ontologyReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_FILTERED_ONTOLOGY_FAILED, should provide error information", () => {
    const action = {
      type: "FETCH_FILTERED_ONTOLOGY_FAILED",
      payload: {
        status: "mock error status",
        statusText: "mock error status text",
        url: "mock error url"
      }
    };
    const { status, statusText, url } = action.payload;
    const expected = Ontology({
      isFetching: false,
      error: {
        hasError: true,
        message: `${status} ${statusText}`,
        url
      },
      root: [],
      terms: new Map(),
      filterOptions: null,
      filteredTree: null,
      textFilter: null,
      filterValue: null
    });
    const actual = ontologyReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_FILTERED_ONTOLGOY_SUCCEEDED, should update filteredTree property", () => {
    const action = {
      type: "FETCH_FILTERED_ONTOLOGY_SUCCEEDED",
      payload: {
        results: [{ path: "item1" }, { path: "item2" }, { path: "item3" }]
      }
    };
    const expected = Ontology({
      filteredTree: {
        item1: { path: "item1", children: {} },
        item2: { path: "item2", children: {} },
        item3: { path: "item3", children: {} }
      },
      isFetching: false,
      startFilterIndex: 0,
      canLoadMoreFilteredResults: false,
      filterIndex: 1
    });
    const actual = ontologyReducer(undefined, action);
    expect(actual).toEqual(expected);
  });
});
