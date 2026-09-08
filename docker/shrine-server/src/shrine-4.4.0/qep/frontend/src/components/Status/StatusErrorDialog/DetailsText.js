import React from "react";
import PropTypes from "prop-types";

import { DEFAULT_DIGEST_PROPERTY_MESSAGE } from "models";

export function DetailsText({ detailsText }) {
  const detailsJSXString = detailsText
    .replace(/<details\s*\/>/gi, DEFAULT_DIGEST_PROPERTY_MESSAGE)
    .replace(/(<details>|<\/details>|<\/line>)/gi, "")
    .replace(/(<line>|,)/gi, "<br/>");

  return (
    <div
      className="DetailsText"
      dangerouslySetInnerHTML={{ __html: detailsJSXString }}
    />
  );
}

DetailsText.propTypes = {
  detailsText: PropTypes.string.isRequired
};
