import React from "react";
import { mount } from "enzyme";
import { shallowToJson } from "enzyme-to-json";
import { QueryNameAndNotesDialog } from "../QueryNameAndNotesDialog";

describe("QueryNameAndNotesDialog", () => {
  const props = {
    open: true,
    currentQueryName: "my query name",
    currentQueryNotes: "my query notes",
    onClose: jest.fn()
  };

  it("should not call onClose when submit button is clicked but the query name has not been changed", () => {
    const output = mount(<QueryNameAndNotesDialog {...props} />);
    output
      .find(".submit-button")
      .at(1)
      .simulate("click");

    expect(props.onClose).not.toHaveBeenCalled();
  });

  xit("should call onClose when submit button is clicked and the query name has been changed", () => {
    const output = mount(<QueryNameAndNotesDialog {...props} />);
    output
      .find(".query-name-input")
      .at(0)
      .simulate("change", { target: { value: "my query name modified" } });
    output
      .find(".submit-button")
      .at(0)
      .simulate("click");

    expect(props.onClose).toHaveBeenCalled();
  });

  it("should render correctly", () => {
    const output = mount(<QueryNameAndNotesDialog {...props} />);
    expect(shallowToJson(output)).toMatchSnapshot();
  });
});
