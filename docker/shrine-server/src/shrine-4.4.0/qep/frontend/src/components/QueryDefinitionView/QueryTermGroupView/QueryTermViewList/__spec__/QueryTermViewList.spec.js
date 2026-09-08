import React from "react";
import { mount, shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import QueryTermViewList from "..";

describe("QueryTermViewList", () => {
  const props = {
    terms: [
      {
        name: "mock diagnosis",
        path: "mock path",
        key: "mock/term/path/mockdiagnosis",
      },
      {
        name: "mock demographic",
        path: "mock path",
        key: "mock/term/path/mockdemographic",
      },
      {
        name: "mock lab",
        path: "mock path",
        key: "mock/term/path/mocklab",
      },
    ],
    onDeleteTermClicked: jest.fn(),
    groupId: "group_1",
  };
  const output = shallow(<QueryTermViewList {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
