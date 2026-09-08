import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { Tree } from "../Tree";

describe("Tree", () => {
  const props = {
    conceptList: [],
    fetchChildren: jest.fn()
  };
  const output = mount(<Tree {...props} />);

  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
