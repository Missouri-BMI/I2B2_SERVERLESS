import { put, call } from "redux-saga/effects";

import { doStartQuery } from "../startQuerySaga";
import { secureFetchOrLogout } from "../../utilities";

describe("startQuerySaga", () => {
  const action = {
    payload: {
      queryName: "TestQuery 2020",
      queryNotes: "TestQuery 2020, notes",
      queryFaved: false,
      timelineGroupId: null,
      queryTermGroups: new Map([
        [
          "group_1",
          {
            concepts: [
              {
                displayName: "35-44 years old",
                path: "\\\\ACT\\Demographics\\Age\\35-44 years old\\",
                constraint: {
                  constraintType: "ANY",
                  values: [],
                  unit: "mmol/L",
                },
              },
            ],
            isExcluded: false,
            options: {
              startDate: null,
              endDate: null,
              occurrences: 1,
            },
          },
        ],
      ]),
    },
  };

  const expectedQuery = {
    name: "TestQuery 2020",
    conceptGroups: [
      {
        concepts: [
          {
            displayName: "35-44 years old",
            path: "\\\\\\\\ACT\\\\Demographics\\\\Age\\\\35-44 years old\\\\",
            constraint: null,
          },
        ],
        isExcluded: false,
        options: {
          startDate: null,
          endDate: null,
          occurrences: 1,
        },
      },
    ],
    timeline: null,
  };

  const generator = doStartQuery(action);

  it("Should run query", () => {
    const mockUrl = "/shrine-api/qep/startQuery";
    const headers = {
      "Content-Type": "application/json",
    };
    const expected = call(secureFetchOrLogout, mockUrl, {
      headers,
      method: "POST",
      body: JSON.stringify(expectedQuery),
    });
    const actual = generator.next().value;
    expect(actual).toBeTruthy();
  });

  it("Should dispatch a start query succeeded action", () => {
    const expected = put({ type: "START_QUERY_SUCCEEDED", payload: {} });
    const actual = generator.next({ ok: true, data: "mock data result" }).value;
    expect(actual).toEqual(expected);
  });

  it("Should dispatch a UPDATE_VIEW_MODE action if run was successful", () => {
    const expected = put({
      type: "UPDATE_VIEW_MODE",
      payload: "QUERY_RESULTS",
    });
    const actual = generator.next().value;
    expect(actual).toEqual(expected);
  });

  it("Should dispatch a START_QUERY_POLL action if run was successful", () => {
    const action = {
      type: "START_QUERY_POLL",
      payload: {},
    };

    const expected = put(action);
    const actual = generator.next({
      query: {},
    }).value;
    expect(actual).toEqual(expected);
  });
});
