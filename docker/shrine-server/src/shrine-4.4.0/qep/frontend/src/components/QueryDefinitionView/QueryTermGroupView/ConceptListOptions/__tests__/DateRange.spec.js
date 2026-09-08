import React from "react";
import { shallow, mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import DateRange from "../DateRange";

describe("DateRange", () => {
  let startDate = 0;
  let endDate = 0;
  const onStartDateChange = jest.fn((date) => {
    startDate = date;
  });
  const onEndDateChange = jest.fn((date) => {
    endDate = date;
  });
  const onClear = jest.fn(() => {
    startDate = null;
    endDate = null;
  });

  const props = {
    startDate,
    endDate,
    onStartDateChange,
    onEndDateChange,
    onClear,
  };
  const output = mount(<DateRange {...props} />);

  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
