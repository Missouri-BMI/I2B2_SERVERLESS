import PropTypes from "prop-types";

export const DEFAULT_DIGEST_PROPERTY_MESSAGE = "No details available";
export const WIKI_BASE_URL =
  "https://open.med.harvard.edu/wiki/display/SHRINE/";

export const ProblemDigest = ({
  codec = DEFAULT_DIGEST_PROPERTY_MESSAGE,
  summary = DEFAULT_DIGEST_PROPERTY_MESSAGE,
  description = DEFAULT_DIGEST_PROPERTY_MESSAGE,
  stampText = DEFAULT_DIGEST_PROPERTY_MESSAGE,
  detailsText = null
} = {}) => {
  const hasDetailsText = !!detailsText;

  return {
    codec,
    summary,
    description,
    stampText,
    hasDetailsText,
    detailsText: hasDetailsText ? detailsText : DEFAULT_DIGEST_PROPERTY_MESSAGE
  };
};

ProblemDigest.propTypes = {
  codec: PropTypes.string,
  summary: PropTypes.string,
  description: PropTypes.string,
  stampText: PropTypes.string,
  hasDetailsText: PropTypes.bool,
  detailsText: PropTypes.string
};
