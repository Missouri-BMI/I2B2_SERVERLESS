import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { DefaultContent } from "../DefaultContent";

describe("DefaultContent", () => {
  const output = shallow(<DefaultContent />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
