import PropTypes from "prop-types";

import { Error } from "./Error";

export const User = ({
  username = null,
  isAuthenticating = false,
  loginAttempts = 0,
  isAuthenticated = false,
  wasLoggedIn = false,
  error = Error(),
  config = {}
} = {}) => ({
  username,
  isAuthenticating,
  loginAttempts,
  isAuthenticated,
  wasLoggedIn,
  error,
  config
});

User.propTypes = {
  username: PropTypes.string,
  isAuthenticating: PropTypes.bool,
  loginAttempts: PropTypes.number,
  isAuthenticated: PropTypes.bool,
  error: PropTypes.shape(Error.propTypes),
  config: PropTypes.shape({}),
};
