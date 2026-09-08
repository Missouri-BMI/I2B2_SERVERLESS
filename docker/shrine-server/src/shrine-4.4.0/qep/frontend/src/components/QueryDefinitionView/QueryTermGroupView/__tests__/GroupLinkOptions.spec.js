import {mount, shallow} from "enzyme";
import GroupLinkOptions from "../GroupLinkOptions";
import { GroupLinkType, QueryTermGroupStatusTypes } from "models";

import {shallowToJson} from "enzyme-to-json";
import React from "react";
import {QueryResultView} from "../../../QueryResultView";

describe("GroupLinkOptions", () => {
  const props = {
    updateGroupOptions: jest.fn(),
    group: {
      options: {
        startDate: null,
        endDate: null,
        occurrences: 1,
        linkedBy: null
      }
    }
  };
  const output = mount(<GroupLinkOptions {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("Same instance icon should be displayed if link type is same instance", () => {
    const props = {
      updateGroupOptions: jest.fn(),
      group: {
        options: {
          startDate: null,
          endDate: null,
          occurrences: 1,
          linkedBy: GroupLinkType.SameInstance
        }
      }
    };

    const output = shallow(<GroupLinkOptions {...props} />);
    expect(output.find('.instLink').length).toBe(1);
  });

  it("Same encounter icon should be displayed if link type is same encounter", () => {
    const props = {
      updateGroupOptions: jest.fn(),
      group: {
        options: {
          startDate: null,
          endDate: null,
          occurrences: 1,
          linkedBy: GroupLinkType.SameEncounter
        }
      }
    };

    const output = shallow(<GroupLinkOptions {...props} />);
    expect(output.find('.encLink').length).toBe(1);
  });
});
