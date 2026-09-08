import PropTypes from "prop-types";

export const Error = ({
  hasError = false,
  message = null,
  url = null
} = {}) => ({
  hasError,
  message,
  url
});

Error.propTypes = {
  hasError: PropTypes.bool.isRequired,
  message: PropTypes.string,
  url: PropTypes.string
};
