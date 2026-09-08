import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import TypeSelect from "../TypeSelect";
import {MenuItem} from "@material-ui/core";

describe("TypeSelect", () => {
  const props = {
    labDetailOptionList: [
      (<MenuItem value="FLAG 1">Abnormal Flag: flag 1</MenuItem>),
      (<MenuItem value="FLAG 2">Abnormal Flag: flag 2</MenuItem>),
      (<MenuItem value="EQ">Equal To (=)</MenuItem>),
      (<MenuItem value="LT">Less Than {'(<)'}</MenuItem>),
      (<MenuItem value="YES">Yes</MenuItem>),
      (<MenuItem value="NO">No</MenuItem>),
    ],
    visibilityClass: "mock-visibility-class",
    type: "mock-type",
    onTypeChange: jest.fn()
  };
  const output = shallow(<TypeSelect {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("onTypeChange should be called when a type has been selected", () => {
    output.find(".TypeSelect").simulate("change");
    expect(props.onTypeChange).toHaveBeenCalled();
  });
});
