import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { SelectedQuery } from "../../../models";
import { WrappedQueryResultContainer } from "..";

describe("QueryContainer", () => {
  it("should render correctly", () => {
    const props = {
      selectedQuery: SelectedQuery(),
      dispatch: jest.fn(),
    };

    const output = shallow(<WrappedQueryResultContainer {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
