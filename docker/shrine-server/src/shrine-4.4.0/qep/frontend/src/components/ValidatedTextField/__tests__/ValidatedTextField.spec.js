import React from "react";
import { shallow, mount, render } from "enzyme";
import { shallowToJson } from "enzyme-to-json";

import ValidatedTextField from "..";

describe("ValidatedTextField", () => {

  it("should render correctly", () => {
    const props = {
      onTextChange: () => {},
      name: "Test Field",
      maxCharacters: 10
    };
    const output = shallow(<ValidatedTextField {...props} />);

    expect(shallowToJson(output)).toMatchSnapshot();
  });

  /**
   * initial validation, before user interaction
   */

  it("should not display an error when initialized with a valid value, but should call onTextChange() with correct textValue, textValid and name", () => {

    const props = {
      onTextChange: jest.fn(),
      name: "Test Field",
      id: "textField",
      value: "a valid value Ç"
    };
    const output = mount(<ValidatedTextField {...props} />);

    // check that onTextChange was called once - it's part of the initial validation
    expect(props.onTextChange).toHaveBeenCalledTimes(1);
    expect(props.onTextChange).toHaveBeenCalledWith("a valid value Ç", true, "Test Field");

    // check that there are no error messages displayed
    const errorDiv = output.find("div.Mui-error");
    expect(errorDiv.length).toBe(0);
    const errorP = output.find("p.Mui-error");
    expect(errorP.length).toBe(0);

  });

  it("should not display an error when initial value is empty, and should not call onTextChange()", () => {

    const props = {
      onTextChange: jest.fn(),
      name: "Test Field",
      id: "textField",
      value: ""
    };
    const output = mount(<ValidatedTextField {...props} />);

    // check that onTextChange was not called (because value is empty)
    expect(props.onTextChange).toHaveBeenCalledTimes(0);

    // check that there are no error messages displayed
    const errorDiv = output.find("div.Mui-error");
    expect(errorDiv.length).toBe(0);
    const errorP = output.find("p.Mui-error");
    expect(errorP.length).toBe(0);

  });

  const executeInvalidInitialValueSpec = (maxChar, initialValue, fieldName, expectedMessage) => {

      const props = {
        maxTextLength: maxChar,
        onTextChange: jest.fn(),
        id: "textField",
        name: fieldName,
        value: initialValue
      };
      const output = mount(<ValidatedTextField {...props} />);

      // check that onTextChange was called once - it's part of the initial validation
      expect(props.onTextChange).toHaveBeenCalledTimes(1);
      expect(props.onTextChange).toHaveBeenCalledWith(initialValue, false, fieldName);

      // check that there are no error messages displayed
      const errorDiv = output.find("div.Mui-error");
      expect(errorDiv.length).toBe(1);
      const errorP2 = output.find("p.Mui-error");
      expect(errorP2.length).toBe(1);
      expect(errorP2.text()).toBe(expectedMessage);

  };

  it("should display an error when initial value is invalid, and should call onTextChange() with textValue, textValid and name", () => {

    executeInvalidInitialValueSpec(5, "123456", "Test Field", "Test Field must be < 5 characters");
    executeInvalidInitialValueSpec(100, " ", "Test Field", "invalid leading whitespace");
    // We are now allowing all characters, so the following is no longer a validation failure
    // executeInvalidInitialValueSpec(100, "vvaÇ", "Test Field", "invalid special character: Ç");

  });

  /**
   * input value changed by user
   */

  const executeValidateChangeToFieldValue = (overrideProps, changedValue, fieldName, expectedErrorMessage = null) => {

    const props = {
      ...overrideProps,
      onTextChange: jest.fn(),
      name: fieldName,
      id: "textField",
      value: "a valid value"
    };
    const output = mount(<ValidatedTextField {...props} />);

    // check that onTextChange was called once - it's part of the initial validation
    expect(props.onTextChange).toHaveBeenCalledTimes(1);
    expect(props.onTextChange).toHaveBeenCalledWith("a valid value", true, "Test Field");

    // check that there are no error messages displayed
    const errorDiv = output.find("div.Mui-error");
    expect(errorDiv.length).toBe(0);
    const errorP = output.find("p.Mui-error");
    expect(errorP.length).toBe(0);

    // Simulate using input
    const textField2 = output
      .find("input")
      .at(0);
    expect(textField2.length).toBe(1);
    textField2.simulate("change", { target: { value: changedValue } });

    if (expectedErrorMessage === null) {
      // no error expected
      const errorDiv2 = output.find("div.Mui-error");
      expect(errorDiv2.length).toBe(0);
      const errorP2 = output.find("p.Mui-error");
      expect(errorP2.length).toBe(0);
      expect(props.onTextChange).toHaveBeenCalledTimes(2);
      expect(props.onTextChange).toHaveBeenLastCalledWith(changedValue, true, fieldName);
    }
    else {
      // check that error messages are displayed
      const errorDiv2 = output.find("div.Mui-error");
      expect(errorDiv2.length).toBe(1);
      const errorP2 = output.find("p.Mui-error");
      expect(errorP2.length).toBe(1);
      expect(errorP2.text()).toBe(expectedErrorMessage);
      expect(props.onTextChange).toHaveBeenCalledTimes(2);
      expect(props.onTextChange).toHaveBeenLastCalledWith(changedValue, false, fieldName);
    }

  };

  /**
   * Test validation using default rules
   */
  it("using default prop values, should display an error when initialized with valid value but changed to an invalid value, and should call onTextChange() with textValue, textValid, and fieldName", () => {

    executeValidateChangeToFieldValue( {}, " an invalid value", "Test Field", "invalid leading whitespace");
    executeValidateChangeToFieldValue({}, "", "Test Field", "required");
    executeValidateChangeToFieldValue({},
      "12345678901234567890123456789012345678901234567890" +
      "12345678901234567890123456789012345678901234567890" +
      "12345678901234567890123456789012345678901234567890" +
      "12345678901234567890123456789012345678901234567890" +
      "1234567890123456789012345678901234567890123456789" + "1",
      "Test Field", "Test Field must be < 250 characters");
    // TODO-XH: comment this back in if we decide to not allow ALL characters
    // executeValidateChangeToFieldValue( {}, "Ç", "Test Field", "invalid special character: Ç");

  });

  /**
   * Test validation using overridden rules
   */
  it("with overridden validation rules, should display an error when initialized with valid value but changed to an invalid value, and should call onTextChange() with textValue, textValid, and fieldName", () => {

    executeValidateChangeToFieldValue( {maxTextLength: 15},  "an invalid value","Test Field", "Test Field must be < 15 characters");
    // The following scenario correctly does not produce an error
    executeValidateChangeToFieldValue({allowLeadingSpace: true}, " ", "Test Field", null);
    executeValidateChangeToFieldValue({invalidCharsRegex: "[^ a-z]"}, "A", "Test Field", "invalid input: A");

  });

});
