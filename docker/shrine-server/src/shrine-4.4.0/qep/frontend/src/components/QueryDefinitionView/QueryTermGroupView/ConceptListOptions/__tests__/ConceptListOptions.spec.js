import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { ConceptListOptions } from "..";
import { QueryTermGroupOptions } from "models";

describe("ConceptListOptions", () => {
  const props = {
    queryTermGroupOptions: QueryTermGroupOptions(),
    onChange: jest.fn(),
  };
  const output = mount(<ConceptListOptions {...props} />);

  // since start date is different every time this runs...this snapshot test will fail.
  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
