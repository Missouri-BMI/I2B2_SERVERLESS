import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import ValueRange from "../ValueRange";

describe("ValueRange", () => {
  const props = {
    startValue: 1,
    endValue: 2,
    startLabel: "",
    endLabel: "",
    isStartError: false,
    isEndError: false,
    visibilityClass: "mock-visibility-class",
    onStartChange: jest.fn(),
    onEndChange: jest.fn()
  };
  const output = shallow(<ValueRange {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("onLowValueChange should be called when text is entered into low value input", () => {
    output.find(".LowValue").simulate("change");
    expect(props.onStartChange).toHaveBeenCalled();
  });
  it("onHighValueChange should be called when text is entered into high value input", () => {
    output.find(".HighValue").simulate("change");
    expect(props.onEndChange).toHaveBeenCalled();
  });
});
