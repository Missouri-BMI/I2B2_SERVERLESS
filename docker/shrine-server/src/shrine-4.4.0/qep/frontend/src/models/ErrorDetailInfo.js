import PropTypes from "prop-types";

import {
  ProblemDigest,
  DEFAULT_DIGEST_PROPERTY_MESSAGE
} from "./ProblemDigest";

export const ErrorDetailInfo = ({
  status = DEFAULT_DIGEST_PROPERTY_MESSAGE,
  hasError = false,
  problemDigest = null
} = {}) => {
  const hasProblemDigest = !!problemDigest;

  return {
    status,
    hasProblemDigest: !!problemDigest,
    hasError,
    problemDigest: hasProblemDigest
      ? ProblemDigest(problemDigest)
      : DEFAULT_DIGEST_PROPERTY_MESSAGE
  };
};

ErrorDetailInfo.propTypes = {
  status: PropTypes.string,
  hasError: PropTypes.bool,
  hasProblemDigest: PropTypes.bool,
  problemDigest: PropTypes.oneOfType([
    PropTypes.shape(ProblemDigest.propTypes),
    PropTypes.string
  ])
};
