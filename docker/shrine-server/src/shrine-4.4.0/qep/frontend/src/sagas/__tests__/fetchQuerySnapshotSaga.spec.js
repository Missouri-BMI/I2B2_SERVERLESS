import { call } from "redux-saga/effects";

import { doFetchQuerySnapshot } from "../fetchQuerySnapshotSaga";
import {secureFetchOrLogout} from "../../utilities";

describe("fetchQuerySnapshotSaga", () => {
  const action = {
    payload: {
      url: "mock url",
      isQueryUpdateRequest: false,
      queryId: "mock queryId",
    },
  };
  const generator = doFetchQuerySnapshot(action);
  it("Should fetch a query snapshot", () => {
    const expected = call(secureFetchOrLogout, action.payload.url);
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });
});
