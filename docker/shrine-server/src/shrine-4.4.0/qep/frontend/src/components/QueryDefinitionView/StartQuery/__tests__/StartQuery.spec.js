import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { DataDistributionTypes } from "models";
import { StartQuery } from "..";

describe("StartQuery", () => {
  it("should render correctly", () => {
    const props = {
      dataDistributionTypes: DataDistributionTypes(),
      queryName: null,
      networkName: "XYZ",
      isEnabled: false,
    };
    const output = shallow(<StartQuery {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("should enable Get Counts button if isEnabled is true", () => {
    const props = {
      dataDistributionTypes: DataDistributionTypes(),
      queryName: null,
      networkName: "XYZ",
      isEnabled: true,
    };
    const output = shallow(<StartQuery {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
