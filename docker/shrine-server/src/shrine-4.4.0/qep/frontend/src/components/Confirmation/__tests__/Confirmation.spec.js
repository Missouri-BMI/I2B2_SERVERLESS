import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import Confirmation from "..";

describe("Confirmation", () => {
  const props = {
    onCancel: jest.fn(),
    onOk: jest.fn(),
    text: "mock confirmation text",
  };
  const output = mount(<Confirmation {...props} />);

  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("It should call onOk when ok is clicked", () => {
    expect(props.onOk).not.toHaveBeenCalled();
    output.find("button").at(0).simulate("click");
    expect(props.onOk).toHaveBeenCalled();
  });

  it("It should call onCancel when cancel is clicked", () => {
    expect(props.onCancel).not.toHaveBeenCalled();
    output.find("button").at(1).simulate("click");
    expect(props.onCancel).toHaveBeenCalled();
  });
});
