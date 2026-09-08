import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { QueryResult } from "../../../models";
import { QueryResultPanel } from "../QueryResultPanel";

describe("QueryResultPanel", () => {
  const props = QueryResult();

  // central time vs eastern time causes this issue.
  xit("should render correctly", () => {
    const query = { ...props, queryName: "Query 1", updated: 12345678 };
    const output = shallow(<QueryResultPanel query={query} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
