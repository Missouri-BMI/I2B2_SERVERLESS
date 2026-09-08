import React from "react";
import {mount, shallow} from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import GroupHeader from "../GroupHeader";
import { QueryTermGroupStatusTypes } from "models";

describe("GroupHeader", () => {
  const props = {
    onRadioChange: jest.fn(),
    isExcluded: false,
    activeClass: "mock-active-class",
    onClearAllTermsClicked: jest.fn(),
    hasChildren: false,
    status: QueryTermGroupStatusTypes.INCLUDED,
    group: {
      options: {
        startDate: null,
        endDate: null,
        occurrences: 1,
        linkedBy: null
      }
    }
  };
  const output = mount(<GroupHeader {...props} />);


  xit("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("With should be checked if status is INCLUDED ", () => {
    expect(output.find("input").get(0).props.value).toBe("INCLUDED");
    expect(output.find("input").get(0).props.checked).toBe(true);
  });

  it("Without should not be checked if isExcluded is false", () => {
    expect(output.find("input").get(1).props.value).toBe("EXCLUDED");
    expect(output.find("input").get(1).props.checked).toBe(false);
  });

  it("onRadioChange should be called if Without is clicked", () => {
    output.find('input[value="EXCLUDED"]').simulate("change");
    expect(props.onRadioChange).toHaveBeenCalled();
  });

  const output2 = mount(<GroupHeader {...{ ...props, status: QueryTermGroupStatusTypes.EXCLUDED }} />);
  it("With should not be checked if status is EXCLUDED", () => {
    expect(output2.find("input").get(0).props.value).toBe("INCLUDED");
    expect(output2.find("input").get(0).props.checked).toBe(false);
  });

  it("Without should be checked if status is EXCLUDED", () => {
    expect(output2.find("input").get(1).props.value).toBe("EXCLUDED");
    expect(output2.find("input").get(1).props.checked).toBe(true);
  });

  it("Display time icon for temporal query", () => {
    const props = {
      updateGroupOptions: jest.fn(),
      status: QueryTermGroupStatusTypes.TIMELINE,
      group: {
        options: {
          startDate: null,
          endDate: null,
          occurrences: 1,
          linkedBy: null
        }
      }
    };

    const output = shallow(<GroupHeader {...props} />);
    expect(output.find('.event-icon').length).toBe(1);
  });
});
