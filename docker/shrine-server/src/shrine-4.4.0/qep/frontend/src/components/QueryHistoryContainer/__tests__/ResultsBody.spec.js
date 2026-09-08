import React, { useContext } from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { QueryResult } from "models";
import { ResultsBody } from "../ResultsBody";
import { QueryHistoryContext } from "../QueryHistoryContext";

describe("ResultsBody", () => {
  const props = {
    results: [
      QueryResult({
        problemDigest: {},
        queryName: "mock query name",
        queryAsString: "Query criteria",
        queryId: "12345",
        queryFaved: null,
        changeDate: "12345",
        dateCreated: "12345",
        statusMsg:
          "There are no query results to display. Please select or run a query.",
        breakdownMsg:
          "There are no breakdowns to display. Please select or run a query.",
        status: "mock status",
        internalStatus: "mock internal status"
      })
    ],
    selectedResultId: 0,
    loadResult: jest.fn(),
    showPageBack: true,
    showPageForward: true,
    pageBack: jest.fn(),
    pageForward: jest.fn()
  };

  it("should render correctly", () => {
    const output = shallow(
      <QueryHistoryContext.Provider value={{ ...props }}>
        <ResultsBody />
        );
      </QueryHistoryContext.Provider>
    );
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
