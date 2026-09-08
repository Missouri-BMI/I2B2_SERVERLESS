import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { QueryResult, Error } from "models";
import { ResultRow } from "../ResultRow";
import { QueryHistoryContext } from "../QueryHistoryContext";

describe("ResultRow", () => {
  const props = {
    result: QueryResult({
      problemDigest: {},
      dataVersion: -1,
      queryName: "mock query name",
      queryId: "12345",
      queryFaved: null,
      updated: "12345",
      dateCreated: "12345",
      statusMsg:
        "There are no query results to display. Please select or run a query.",
      breakdownMsg:
        "There are no breakdowns to display. Please select or run a query.",
      error: Error(),
      isPolling: false,
      breakdowns: [],
      institutionResults: [],
      status: "mock status",
      internalStatus: "mock internal status",
      isFetching: false
    }),
    loadResult: jest.fn(),
    handleFavQuery: jest.fn(),
    favTexts: {
      favInstructions: "mock faving instructions",
      favPlaceholderText: "mock faving placeholder text"
    },
    handleRenameQuery: jest.fn(),
    selected: false
  };
  xit("should render correctly", () => {
    const output = shallow(<ResultRow {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
  it("Onclick should respond to user click", () => {
    const output = mount(
      <QueryHistoryContext.Provider
        value={{
          loadResult: props.loadResult,
          handleFavQuery: props.handleFavQuery,
          favTexts: props.favTexts,
          handleRenameQuery: props.handleRenameQuery,
          selectedQueryId: props.result.queryId
        }}
      >
        <ResultRow result={props.result} />
      </QueryHistoryContext.Provider>
    );

    output.simulate("click");
    expect(props.loadResult).toHaveBeenCalledWith("12345", "mock query name");
  });
});
