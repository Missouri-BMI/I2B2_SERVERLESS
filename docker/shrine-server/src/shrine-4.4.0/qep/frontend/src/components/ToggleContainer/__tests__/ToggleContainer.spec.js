import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import ToggleContainer from "..";

describe("ToggleContainer", () => {
  function MockChildComponent() {
    return <div className="MockChildComponent">mock child</div>;
  }
  let isOpen = true;
  let props = {
    isOpen,
    onClose: jest.fn()
  };
  const output = mount(
    <ToggleContainer {...props}>
      <MockChildComponent />
    </ToggleContainer>
  );

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("should call onClose when the minimize button is clicked", () => {
    expect(output.find(".open").length).toBe(1);
    output.find(".minimize-toggle-container").simulate("click");
    expect(props.onClose).toHaveBeenCalled();
  });

  it("should not render a child component if isOpen is false", () => {
    const newProps = { ...props, isOpen: false };
    const newOutput = mount(
      <ToggleContainer {...newProps}>
        <MockChildComponent />
      </ToggleContainer>
    );
    expect(newOutput.find(".open").length).toBe(0);
  });
});
