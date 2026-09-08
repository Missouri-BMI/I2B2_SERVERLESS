import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";
import "isomorphic-fetch";
import DropDown from "..";

describe("DropDown", () => {
  it("should render correctly", () => {
    const options = new Map([
      ["All Concepts", "All Concepts"],
      ["ACT Demographics", "ACT Demographics"],
      ["ACT Diagnosis ICD-10", "ACT Diagnosis ICD-10"],
      ["ACT Diagnosis ICD10-ICD9", "ACT Diagnosis ICD10-ICD9"],
      ["ACT Laboratory Tests", "ACT Laboratory Tests"],
      ["ACT Procedures ICD-9-Proc", "ACT Procedures ICD-9-Proc"]
    ]);
    const output = mount(<DropDown options={options} />);

    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
