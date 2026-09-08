import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { User, NetworkConfig } from "models";
import { HeaderMenu } from "../HeaderMenu";

describe("HeaderMenu", () => {
  const props = {
    user: User({}),
    networkConfig: NetworkConfig({})
  };

  const output = mount(<HeaderMenu {...props} />);
  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
