import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import UnitSelect from "../UnitSelect";

describe("UnitSelect", () => {
  const props = {
    value: "unit1",
    onChange: jest.fn(),
    units: ["unit1", "unit2", "unit3"],
    visibilityClass: "mock-visibility-class"
  };
  const output = shallow(<UnitSelect {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("Should display the unit select if there are multiple units", () => {
    expect(output.find(".unit-selector").length).toBe(1);
    expect(output.find(".unit-label").length).toBe(0);
  });

  it("OnChange should be called when a unit is selected", () => {
    output.find(".unit-selector").simulate("change");
    expect(props.onChange).toHaveBeenCalled();
  });

  it("Should not display the unit select if the units length is 1", () => {
    const newProps = { ...props, units: ["mock-unit1"] };
    const output2 = shallow(<UnitSelect {...newProps} />);
    expect(output2.find(".unit-selector").length).toBe(0);
    expect(output2.find(".unit-label").length).toBe(1);
  });
});
