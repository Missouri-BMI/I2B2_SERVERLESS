import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import QueryTermLabView from "../QueryTermLabView";

describe("QueryTermLabView", () => {
  const props = {
    term: {
      path: "mock path",
      displayName: "mock name",
      category: "mock category",
      labDetail: {
        flagValues: ["Normal", "High", "Low"],
        units: ["ml", "ltr", "quart"]
      },
      constraint: {
        constraintType: "BETWEEN",
        value: [1, 3],
        unit: "ml"
      }
    },
    onDeleteTermClicked: jest.fn(),
    onConstraintChange: jest.fn(),
  };
  const output = shallow(<QueryTermLabView {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
