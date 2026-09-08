import { TextField } from "@material-ui/core";
import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

export default function ValidatedTextField(props) {
  const {
    onTextChange,
    name,
    maxTextLength,
    allowLeadingSpace,
    invalidCharsRegex,
    required,
    ...muiProps
  } = props;
  const [errorText, setErrorText] = useState("");

  // This component may be initialized with a non-empty value, which we would like to validate immediately,
  // unless it is an empty string
  useEffect(() => {
    if (props.value) validate(props.value);
  }, [props.value]);

  const handleChange = (event) => {
    const textValue = event.target.value;
    validate(textValue);
  };

  const validate = (textValue) => {
    let textValid = false;

    const leadingWhiteSpaceRegex = "^[\\s]+?";

    if (required && !textValue.length) {
      setErrorText(`required`);
    } else if (textValue.length >= maxTextLength) {
      setErrorText(`${name} must be < ${maxTextLength} characters`);
    } else if (invalidCharsRegex && textValue.match(invalidCharsRegex)) {
      // A note about new line characters:
      // If the "multiline" property is set to true, it will be passed to the underlying
      // text field. As a result there is no need to validate against new lines when
      // multiline is false. When it is true, however, new line characters are allowed; if
      // only the wrap-around feature of multi-line text input fields is desired without allowing
      // new line characters, then the new line character should be specified in the invalidCharsRegex property.
      setErrorText(
        `invalid input: ${textValue.match(invalidCharsRegex)[0]}`
      );
      // TODO-XH: looks like the use case for allowing leading space is going away. If so
      // then remove the allowLeadingSpace prop and logic from the component
    }
    else if (!allowLeadingSpace && textValue.match(leadingWhiteSpaceRegex)) {
      setErrorText(`invalid leading whitespace`);
    } else {
      setErrorText("");
      textValid = true;
    }

    if (typeof onTextChange === "function") {
      onTextChange(textValue, textValid, name);
    }
  };

  return (
    <TextField
      error={!!errorText}
      helperText={errorText}
      onChange={handleChange}
      {...muiProps}
    />
  );
}

ValidatedTextField.defaultProps = {
  required: true,
  onTextChange: null,
  name: "Text",
  maxTextLength: 250,
  allowLeadingSpace: false,
  // TODO-XH : if we decide to have a different *default* set of valid characters, change it here
  // For instance we might want to include characters like Ç
  // invalidCharsRegex: "[^ !-~]"
  // the following allows all characters. It effectively "disables" character validation by default
  // Note that currently none of the instances of ValidatedTextField use invalidCharsRegex.
  invalidCharsRegex: "[\!|\^|\`\~]|(\<script\\s)|(\</?script\>)"
};

ValidatedTextField.propTypes = {
  onTextChange: PropTypes.func,
  name: PropTypes.string,
  // TODO-XH: can do away with making maxTextLength required since it has a default
  maxTextLength: PropTypes.number.isRequired,
  allowLeadingSpace: PropTypes.bool,
  invalidCharsRegex: PropTypes.string,
  required: PropTypes.bool,
};
