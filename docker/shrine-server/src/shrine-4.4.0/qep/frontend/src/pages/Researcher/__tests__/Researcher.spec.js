import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { WrappedResearcher } from "..";

describe("Researcher", () => {
  const props = {
    viewMode: "mock view mode",
    dispatch: jest.fn(),
  };

  it("should render correctly", () => {
    const output = shallow(<WrappedResearcher {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
