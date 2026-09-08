import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { ViewModeTypes } from "models";
import { WrappedHeader } from "..";

describe("Header loggin in mode", () => {
  const props = {
    viewMode: ViewModeTypes.QUERY_DEFINITION,
    dispatch: jest.fn(),
    user: {
      isAuthenticated: true
    },
    networkConfig: {
      name: "SHRINE",
      versionData: {
        buildId: "123-mock-build-id"
      }
    }
  };

  const output = mount(<WrappedHeader {...props} />);
  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
  it("It should set the view mode to Query Definition when that tab is clicked", () => {
    output
      .find("button")
      .at(0)
      .simulate("click");
    expect(props.dispatch).toHaveBeenCalledWith({
      payload: "QUERY_DEFINITION",
      type: "UPDATE_VIEW_MODE"
    });
  });
  it("It should set the view mode to View Query Results when that tab is clicked", () => {
    output
      .find("button")
      .at(1)
      .simulate("click");
    expect(props.dispatch).toHaveBeenCalledWith({
      payload: "QUERY_RESULTS",
      type: "UPDATE_VIEW_MODE"
    });
  });
});

describe("Header loggin out mode", () => {
  const props = {
    viewMode: ViewModeTypes.QUERY_DEFINITION,
    networkConfig: {name: "SHRINE"},
    dispatch: jest.fn(),
    user: {
      isAuthenticated: false
    }
  };
  const output = mount(<WrappedHeader {...props} />);
  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
  it("It should not render navigation tabs when user is not logged in", () => {
    expect(output.find(".MuiTabs-scroller button").length).toBe(0);
  });
});
