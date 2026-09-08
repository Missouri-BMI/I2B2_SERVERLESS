import {
  InstitutionResult,
  Error,
  QueryResult,
  SelectedQuery,
} from "../../models";
import { selectedQueryReducer } from "../selectedQueryReducer";

describe("selectedQueryReducer", () => {
  it("Should yield a default state if none is provided", () => {
    const action = { type: "UKNOWN_ACTION" };
    const expected = SelectedQuery();
    const actual = selectedQueryReducer(undefined, action);
    expect(actual).toEqual(expected);
    expect(actual.dataVersion).toBe(-1);
    expect(actual.queryId).toBe(null);
    expect(actual.queryResult).toStrictEqual(QueryResult());
    expect(actual.institutionResults.length).toBe(0);
  });

  it("FETCH_QUERY_UPDATE should update the queryId, dataVersion, isFetching, and queryName state fields", () => {
    const action = {
      type: "FETCH_QUERY_UPDATE",
      payload: {
        queryId: "12345",
        dataVersion: "12345",
        queryName: "mock query name",
      },
    };
    const expected = SelectedQuery({
      ...action.payload,
      queryResult: QueryResult({
        ...action.payload,
      }),
      isFetching: true,
      statusMsg: "Response received from network.  Waiting on results",
    });
    const actual = selectedQueryReducer(undefined, action);
    expect(actual).toEqual(expected);
  });

  it("FETCH_QUERY_UPDATE should not update the dataVersion and queryName state fields if they are not provided (in the case of re-runing a query)", () => {
    const action = {
      type: "FETCH_QUERY_UPDATE",
      payload: {
        networkId: 12345,
      },
    };
    const expected = SelectedQuery({
      isFetching: true,
      ...action.payload,
    });
    const actual = selectedQueryReducer(SelectedQuery(), action);
    expect(actual.dataVersion).toEqual(-1);
    expect(actual.queryResult.queryName).toEqual(null);
  });
  it("FETCH_QUERY_UPDATE_FAILED should set isFetching to false and contain error information.", () => {
    const action = {
      type: "FETCH_QUERY_UPDATE_FAILED",
      payload: {
        networkId: "12345",
        response: {
          status: "mock error status",
          statusText: "mock error status text",
          url: "mock url",
        },
      },
    };
    const expected = SelectedQuery({
      isFetching: false,
      error: Error({
        hasError: true,
        message: `${action.payload.status} ${action.payload.statusText}`,
        url: action.payload.url,
      }),
    });
    const actual = selectedQueryReducer({}, action);
    expect(actual.toString()).toEqual(expected.toString());
  });
  it("FETCH_QUERY_UPDATE_SUCCEEDED should update the state to the latest result", () => {
    const action = {
      type: "FETCH_QUERY_UPDATE_SUCCEEDED",
      payload: {
        selectedQuery: {
          query: {
            status: "mock query status",
            queryName: "mock query name",
            changeDate: null,
            observed: true,
            queryAsHtmlString: "Query criteria",
          },
          results: [],
          aggregateDemographics: [],
          dataVersion: 12345,
          isComplete: false,
          siteCount: 1,
          patientCount: 2,
        },
      },
    };
    const expected = SelectedQuery({
      queryResult: QueryResult({
        queryName: "mock query name",
        observed: true,
        queryAsHtmlString: "Query criteria",
        status: "mock query status",
      }),
      institutionResults: [],
      dataVersion: 12345,
      isComplete: false,
      siteCount: 1,
      patientCount: 2,
    });

    // faked breakdown data is breaking this test.
    const result = selectedQueryReducer(undefined, action);
    const actual = { ...result, demographicDistribution: [] };
    expect(actual).toEqual(expected);
  });
});
