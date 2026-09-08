import { AllQueries, Error, QueryResult } from "../../models";
import { allQueriesReducer } from "../allQueriesReducer";

describe("allQueriesReducer", () => {
  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };
    const expected = AllQueries();
    const actual = allQueriesReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("If limit and sortBy are provided, it should update the limit, sortBy and isFetching properties on FETCH_ALL_QUERIES", () => {
    const action = {
      type: "FETCH_ALL_QUERIES",
      payload: {
        skip: 127,
        sortBy: "dateCreated.desc"
      }
    };
    const expected = {
      ...AllQueries(),
      isFetching: true,
      skip: 127
    };
    const actual = allQueriesReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("All queries should update it's sortBy property if SORT_ALL_QUERIES is dispatched", () => {
    const action = {
      type: "SORT_ALL_QUERIES",
      payload: {
        sortBy: "mock sort by"
      }
    };
    const expected = {
      ...AllQueries({ sortBy: "mock sort by" }),
      isFetching: true
    };
    const actual = allQueriesReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("By default, all queries should be sorted by dateCreated.desc", () => {
    const action = {
      type: "SORT_ALL_QUERIES",
      payload: {}
    };
    const expected = {
      ...AllQueries({ sortBy: "dateCreated.desc" }),
      isFetching: true
    };
    const actual = allQueriesReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_ALL_QUERIES_FAILED, should provide error information", () => {
    const action = {
      type: "FETCH_ALL_QUERIES_FAILED",
      payload: {
        status: "mock error status",
        statusText: "mock error status text",
        url: "mock error url"
      }
    };
    const { status, statusText, url } = action.payload;
    const expected = {
      ...AllQueries(),
      isFetching: false,
      error: Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      })
    };
    const actual = allQueriesReducer(undefined, action);

    expect(actual).toEqual(expected);
  });

  it("FETCH_ALL_QUERIES_SUCCEEDED, should update the results, rowCount, updated, and isFetching properties", () => {
    const action = {
      type: "FETCH_ALL_QUERIES_SUCCEEDED",
      payload: {
        allQueries: [
          {
            changeDate: "1234567890"
          }
        ],
        rowCount: 1
      }
    };
    const expected = {
      ...AllQueries({
        results: [
          QueryResult({
            changeDate: "1234567890"
          })
        ],
        rowCount: 1
      }),
      isFetching: false
    };
    const actual = allQueriesReducer(undefined, action);

    expect(actual).toEqual(expected);
  });
});
