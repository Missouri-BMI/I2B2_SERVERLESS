import React from "react";
import { mount, shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import TimelineLinkView from "..";
import {Timeline, TimelineLink} from "../../../../../models";

describe("TimelineLinkView", () => {
  const props = {
    group: {
      timeline: Timeline()
    },
    timelineLink:  TimelineLink(),
    event1Index: 1,
    event2Index: 2,
    maxEvents: 3,
    updateTimelineLink: jest.fn(),
  };
  const output = shallow(<TimelineLinkView{...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
