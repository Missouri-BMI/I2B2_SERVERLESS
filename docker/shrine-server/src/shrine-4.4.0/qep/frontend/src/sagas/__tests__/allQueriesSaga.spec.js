import { put, select } from "redux-saga/effects";

import { selectLimit, selectSortBy, selectSkip } from "selectors";
import { buildQuerySnapshotRequest } from "../allQueriesSaga";

describe("allQueriesSaga", () => {
  const generator = buildQuerySnapshotRequest();

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
        url,
      },
      type: "FETCH_QUERY_SNAPSHOT",
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});
