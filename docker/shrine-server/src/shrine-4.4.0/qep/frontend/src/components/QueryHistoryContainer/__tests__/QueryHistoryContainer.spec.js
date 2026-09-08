import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { AllQueries, QueryResult, NetworkConfig } from "../../../models";
import { WrappedQueryHistoryContainer } from "..";

describe("QueryHistoryContainer", () => {
  it("should render correctly", () => {
    const props = {
      allQueries: AllQueries(),
      selectedQuery: QueryResult(),
      networkConfig: NetworkConfig(),
      dispatch: jest.fn(),
    };

    const output = shallow(<WrappedQueryHistoryContainer {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
