// emailTo, subject

import React from "react";
import { shallow } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import { EmailInstructions } from "../EmailInstructions";

describe("DetailsText", () => {
  const props = {
    emailTo: "test@mock.com",
    subject: "mock email subject"
  };
  const output = shallow(<EmailInstructions {...props} />);

  it("should render correctly", () => {
    expect(shallowToJson(output)).toMatchSnapshot();
  });

  it("should open the default mail client when the email link is clicked", () => {
    const html = output
      .find("a")
      .at(0)
      .html();

    expect(html.includes(`mailto:${props.emailTo}`)).toBe(true);
  });
});
