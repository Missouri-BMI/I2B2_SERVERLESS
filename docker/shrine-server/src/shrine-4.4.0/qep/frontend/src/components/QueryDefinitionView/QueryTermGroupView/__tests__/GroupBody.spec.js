import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import GroupBody from "../GroupBody";

describe("GroupBody", () => {
  const props = {
    removeAllGroupTerms: jest.fn(),
    group: {
      id: "mock-id",
      children: [
        {
          name: "mock term 1",
          path: "mock/term/path/1",
        },
        {
          name: "mock term 2",
          path: "mock/term/path/2",
        },
        {
          name: "mock term 3",
          path: "mock/term/path/3",
        },
      ],
    },
    isActive: true,
    hasChildren: true,
    activeClass: "mock-active-class",
  };
  const output = shallow(<GroupBody {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("should display the active class", () => {
    expect(output.find(".mock-active-class").length).toBe(1);
  });
});
