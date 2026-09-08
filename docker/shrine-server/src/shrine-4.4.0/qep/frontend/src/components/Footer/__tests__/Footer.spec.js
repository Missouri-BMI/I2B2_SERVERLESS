import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";
import "isomorphic-fetch";
import { WrappedFooter } from "..";

describe("Footer", () => {
  const props = {
    user: {
      isAuthenticated: true
    }
  };
  it("should render correctly", () => {
    const output = mount(<WrappedFooter {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
