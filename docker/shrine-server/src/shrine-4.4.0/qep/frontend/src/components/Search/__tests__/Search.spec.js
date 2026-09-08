import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import Search from "..";

describe("Search", () => {
  const output = shallow(<Search />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
