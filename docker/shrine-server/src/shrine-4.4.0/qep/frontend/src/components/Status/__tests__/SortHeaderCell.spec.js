import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { SortHeaderCell } from "../SortHeaderCell";

describe("SortHeaderCell", () => {
  const props = {
    id: "mock-id",
    isSelected: true,
    onToggle: jest.fn(),
    sortOrder: "asc",
    sortIconClass: "mock-sort-icon"
  };
  const output = shallow(<SortHeaderCell {...props} />);

  it("Should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("Should fire onToggle when clicked", () => {
    output
      .find("span")
      .at(0)
      .simulate("click");

    expect(props.onToggle).toHaveBeenCalledWith(props.id);
  });
});
