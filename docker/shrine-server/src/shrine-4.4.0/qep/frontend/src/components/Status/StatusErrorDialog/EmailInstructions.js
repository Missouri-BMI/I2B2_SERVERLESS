import React from "react";
import PropTypes from "prop-types";

export function EmailInstructions({ emailTo, subject }) {
  const email =
    emailTo === null ? (
      "email"
    ) : (
      <a href={`mailto:${emailTo}?subject=${encodeURI(subject)}`}>email</a>
    );

  return (
    <span>
      If you would like to report this error, please copy the entire message
      below and paste
      <br />
      into an {email} to your local site administrator.
    </span>
  );
}

EmailInstructions.defaultProps = {
  emailTo: null
};

EmailInstructions.propTypes = {
  emailTo: PropTypes.string,
  subject: PropTypes.string.isRequired
};
