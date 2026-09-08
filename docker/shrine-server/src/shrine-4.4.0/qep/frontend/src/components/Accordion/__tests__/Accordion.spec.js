import React from "react";
import { shallow, mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import Accordion from "..";

describe("Accordion", () => {
  const heading = "mock accordion heading";
  const children = <div>mock child component</div>;
  const props = {
    heading,
    renderAsExpanded: false,
    children,
  };
  const output = mount(<Accordion {...props} />);

  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
