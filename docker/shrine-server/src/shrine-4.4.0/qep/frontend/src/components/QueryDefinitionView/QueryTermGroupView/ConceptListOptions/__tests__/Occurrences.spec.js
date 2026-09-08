import React from "react";
import { shallow, mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import Occurrences from "../Occurrences";

describe("Occurrences", () => {
  let occurrences = "";
  const onOcurrencesChange = jest.fn(v => {
    occurrences = v;
  });
  const onResetOcurrences = jest.fn(() => {
    occurrences = "";
  });

  const props = {
    occurrences,
    onOcurrencesChange,
    onResetOcurrences
  };
  const output = mount(<Occurrences {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
