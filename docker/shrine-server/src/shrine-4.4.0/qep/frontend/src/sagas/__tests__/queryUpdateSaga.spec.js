import { put, call, select } from "redux-saga/effects";

import {
  selectLimit,
  selectSortBy,
  selectSortSiteBy,
  selectSkip,
} from "selectors";
import { buildQuerySnapshotRequest } from "../queryUpdateSaga";

describe("doFetchQueryUpdate fetch happy path", () => {
  const action = {
    payload: {
      queryId: 12345,
      dataVersion: -1,
    },
  };
  const generator = buildQuerySnapshotRequest(action);

  it("Should select the limit value off of the application state", () => {
    const expected = select(selectLimit);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should select the sortBy value off of the application state", () => {
    const expected = select(selectSortBy);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should select the sortSoteBy value off of the application state", () => {
    const expected = select(selectSortSiteBy);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should select the skip value off of the application state", () => {
    const expected = select(selectSkip);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should dispatch the fetchQuerySnapshot action", () => {
    const url =
      "/shrine-api/qep/queryResult?limit=undefined&sortBy=undefined&skip=undefined";
    const expected = put({
      payload: {
        url:
          "/shrine-api/qep/queryResult?networkId=12345&limit=undefined&sortBy=undefined&skip=undefined&afterVersion=-1&sortSiteBy=undefined&timeoutSeconds=0",
        isQueryUpdateRequest: true,
        queryId: 12345,
      },
      type: "FETCH_QUERY_SNAPSHOT",
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});
